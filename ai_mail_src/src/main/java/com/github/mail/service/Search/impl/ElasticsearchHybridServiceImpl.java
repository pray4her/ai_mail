package com.github.mail.service.Search.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.github.mail.model.config.Properties.ElasticSearchProperties;
import com.github.mail.model.config.Properties.RagProperties;
import com.github.mail.repo.KnowledgeBase.domain.EsKbChunk;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.Search.ElasticsearchHybridService;
import com.github.mail.utils.ScoreNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch 混合检索服务实现
 * <p>
 * 核心流程:
 * 1. knn 向量召回大候选集 (Top-300)
 * 2. 在候选集中进行 BM25 关键词匹配
 * 3. 分数归一化 + 加权融合
 * 4. Top-K 截断 + 阈值过滤
 *
 * @author Aster
 * @date 2025/12/30
 */
@Slf4j
@Service
public class ElasticsearchHybridServiceImpl implements ElasticsearchHybridService {

    private final ElasticsearchClient esClient;

    @Autowired
    public ElasticsearchHybridServiceImpl(ElasticsearchClient esClient,
                                          ElasticSearchProperties elasticSearchProperties,
                                          RagProperties ragProperties) {
        this.esClient = esClient;

        this.kbChunksIndex = elasticSearchProperties.getKbChunksIndex();
        this.vectorRecallSize = ragProperties.getVectorRecallSize();
        this.bm25Weight = ragProperties.getBm25Weight();
        this.vectorWeight = ragProperties.getVectorWeight();
        this.minScore = ragProperties.getMinScore();
    }

    private String kbChunksIndex;

    private int vectorRecallSize;

    private double bm25Weight;

    private double vectorWeight;

    private double minScore;

    @Override
    public List<RagChunk> hybridSearch(String queryText, float[] queryVector, int topK) {
        try {
            log.info("开始 ES 混合检索: query={}, topK={}", queryText, topK);

            // 【第一步】knn 向量召回大候选集
            List<Float> queryVectorList = new ArrayList<>();
            for (float v : queryVector) {
                queryVectorList.add(v);
            }

            // 构建 knn 查询
            SearchRequest knnRequest = SearchRequest.of(s -> s
                    .index(kbChunksIndex)
                    .size(vectorRecallSize)
                    .knn(k -> k
                            .field("text_vector")
                            .queryVector(queryVectorList)
                            .k(vectorRecallSize)
                            .numCandidates(vectorRecallSize * 2L))
            );

            SearchResponse<EsKbChunk> knnResponse = esClient.search(knnRequest, EsKbChunk.class);

            if (knnResponse.hits().hits().isEmpty()) {
                log.warn("knn 向量召回为空");
                return Collections.emptyList();
            }

            log.info("knn 向量召回完成: 候选数={}", knnResponse.hits().hits().size());

            // 提取候选 chunk_id
            List<Long> candidateIds = knnResponse.hits().hits().stream()
                    .map(hit -> hit.source().getChunkId())
                    .toList();

            // 构建向量分数映射
            Map<Long, Double> knnScoreMap = knnResponse.hits().hits().stream()
                    .collect(Collectors.toMap(
                            hit -> hit.source().getChunkId(),
                            Hit::score
                    ));

            // 【第二步】在候选集中进行 BM25 关键词匹配
            Query idsQuery = IdsQuery.of(i -> i
                    .values(candidateIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList())))._toQuery();

            Query matchQuery = MatchQuery.of(m -> m
                    .field("text_content")
                    .query(queryText))._toQuery();

            Query boolQuery = BoolQuery.of(b -> b
                    .filter(idsQuery)
                    .must(matchQuery))._toQuery();

            SearchRequest bm25Request = SearchRequest.of(s -> s
                    .index(kbChunksIndex)
                    .query(boolQuery)
                    .size(vectorRecallSize)
            );

            SearchResponse<EsKbChunk> bm25Response = esClient.search(bm25Request, EsKbChunk.class);

            if (bm25Response.hits().hits().isEmpty()) {
                log.warn("BM25 关键词过滤后无结果");
                return Collections.emptyList();
            }

            log.info("BM25 关键词过滤完成: 匹配数={}", bm25Response.hits().hits().size());

            // 【第三步】分数归一化
            // knn 分数归一化
            double minKnn = knnScoreMap.values().stream().min(Double::compare).orElse(0.0);
            double maxKnn = knnScoreMap.values().stream().max(Double::compare).orElse(1.0);

            Map<Long, Double> normalizedKnnScores = knnScoreMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> ScoreNormalizer.minMaxNormalize(e.getValue(), minKnn, maxKnn)
                    ));

            // BM25 分数归一化
            List<Hit<EsKbChunk>> bm25Hits = bm25Response.hits().hits();
            double minBm25 = bm25Hits.stream().mapToDouble(Hit::score).min().orElse(0.0);
            double maxBm25 = bm25Hits.stream().mapToDouble(Hit::score).max().orElse(1.0);

            Map<Long, Double> normalizedBm25Scores = bm25Hits.stream()
                    .collect(Collectors.toMap(
                            hit -> hit.source().getChunkId(),
                            hit -> ScoreNormalizer.minMaxNormalize(hit.score(), minBm25, maxBm25)
                    ));

            // 【第四步】分数加权融合
            List<RagChunk> fusedResults = bm25Hits.stream()
                    .map(hit -> {
                        Long chunkId = hit.source().getChunkId();
                        double normBm25 = normalizedBm25Scores.getOrDefault(chunkId, 0.0);
                        double normKnn = normalizedKnnScores.getOrDefault(chunkId, 0.0);

                        // 加权融合
                        double finalScore = bm25Weight * normBm25 + vectorWeight * normKnn;

                        log.debug("Chunk {}: bm25={}, knn={}, final={}",
                                chunkId, normBm25, normKnn, finalScore);

                        return new RagChunk(
                                hit.source().getTextContent(),
                                finalScore,
                                String.valueOf(chunkId)
                        );
                    })
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .filter(chunk -> chunk.getScore() >= minScore)
                    .collect(Collectors.toList());

            log.info("ES 混合检索完成: 最终返回数={}", fusedResults.size());

            // 输出最终结果详情
            for (int i = 0; i < fusedResults.size(); i++) {
                RagChunk chunk = fusedResults.get(i);
                log.info("结果 #{}: chunk_id={}, score={}", i + 1, chunk.getChunkId(), chunk.getScore());
            }

            return fusedResults;

        } catch (Exception e) {
            log.error("ES 混合检索失败", e);
            return Collections.emptyList();
        }
    }


    /**
     * 双路召回模式
     */
    @Override
    public List<RagChunk> hybridSearchParallelRecall(String queryText, float[] queryVector, int topK) {
        try {
            log.info("开始 ES 混合检索（并行召回模式）: query={}, topK={}", queryText, topK);

            // 1. 准备向量数据
            List<Float> queryVectorList = new ArrayList<>(queryVector.length);
            for (float v : queryVector) {
                queryVectorList.add(v);
            }

            // --- 【第一步】并行或同步执行两路召回 ---

            // A. 向量召回 (KNN)
            SearchRequest knnRequest = SearchRequest.of(s -> s
                    .index(kbChunksIndex)
                    .size(vectorRecallSize)
                    .knn(k -> k
                            .field("text_vector")
                            .queryVector(queryVectorList)
                            .k(vectorRecallSize)
                            .numCandidates(vectorRecallSize * 2L))
            );
            SearchResponse<EsKbChunk> knnResponse = esClient.search(knnRequest, EsKbChunk.class);

            // B. 关键词召回 (BM25)
            SearchRequest bm25Request = SearchRequest.of(s -> s
                    .index(kbChunksIndex)
                    .size(vectorRecallSize)
                    .query(q -> q.match(m -> m.field("text_content").query(queryText)))
            );
            SearchResponse<EsKbChunk> bm25Response = esClient.search(bm25Request, EsKbChunk.class);

            // --- 【第二步】提取结果并准备归一化 ---

            Map<Long, Hit<EsKbChunk>> allHitsMap = new HashMap<>();
            Map<Long, Double> knnScores = new HashMap<>();
            Map<Long, Double> bm25Scores = new HashMap<>();

            // 记录 KNN 结果
            for (Hit<EsKbChunk> hit : knnResponse.hits().hits()) {
                Long id = hit.source().getChunkId();
                allHitsMap.put(id, hit);
                knnScores.put(id, hit.score());
            }

            // 记录 BM25 结果
            for (Hit<EsKbChunk> hit : bm25Response.hits().hits()) {
                Long id = hit.source().getChunkId();
                allHitsMap.put(id, hit);
                bm25Scores.put(id, (double) hit.score());
            }

            if (allHitsMap.isEmpty()) {
                return Collections.emptyList();
            }

            // --- 【第三步】鲁棒性归一化 (Min-Max Normalization) ---

            Map<Long, Double> normKnn = safeNormalize(knnScores);
            Map<Long, Double> normBm25 = safeNormalize(bm25Scores);

            // --- 【第四步】加权融合与重排序 ---

            return allHitsMap.values().stream()
                    .map(hit -> {
                        Long id = hit.source().getChunkId();
                        double kScore = normKnn.getOrDefault(id, 0.0);
                        double bScore = normBm25.getOrDefault(id, 0.0);

                        // 最终线性加权公式：Score = w1 * Norm(KNN) + w2 * Norm(BM25)
                        double finalScore = (vectorWeight * kScore) + (bm25Weight * bScore);

                        return new RagChunk(
                                hit.source().getTextContent(),
                                finalScore,
                                String.valueOf(id)
                        );
                    })
                    // 过滤与排序
                    .filter(chunk -> chunk.getScore() >= minScore)
                    .sorted(Comparator.comparingDouble(RagChunk::getScore).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("ES 混合检索失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 安全的 Min-Max 归一化，防止除以零
     */
    private Map<Long, Double> safeNormalize(Map<Long, Double> scores) {
        if (scores.isEmpty()) {
            return Collections.emptyMap();
        }

        double min = scores.values().stream().min(Double::compare).orElse(0.0);
        double max = scores.values().stream().max(Double::compare).orElse(0.0);

        Map<Long, Double> normalized = new HashMap<>();
        double range = max - min;

        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            // 如果分值全部相同，归一化为 1.0 (或 0.5)
            if (range <= 1e-6) {
                normalized.put(entry.getKey(), 1.0);
            } else {
                double norm = (entry.getValue() - min) / range;
                normalized.put(entry.getKey(), norm);
            }
        }
        return normalized;
    }


}
