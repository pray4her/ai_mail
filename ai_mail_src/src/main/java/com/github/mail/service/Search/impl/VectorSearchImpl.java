package com.github.mail.service.Search.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.repo.KnowledgeBase.dao.KbVectorIndexDao;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.repo.KnowledgeBase.domain.KbVectorIndex;
import com.github.mail.repo.KnowledgeBase.domain.ScoredChunk;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.service.Search.VectorSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量存储实现（基于 MySQL 的余弦相似度检索）
 * 
 * @author Aster
 * @date 2025/12/30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchImpl implements VectorSearch {

    private final KbVectorIndexDao vectorIndexDao;
    private final KbDocumentChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;

    /**
     * 使用余弦相似度搜索最相关的 chunk
     *
     * @param queryVector 查询向量
     * @param topK        返回前 K 个结果
     * @return 相关的 RagChunk 列表（按相似度降序，包含分数）
     */
    @Override
    public List<RagChunk> search(float[] queryVector, int topK) {
        try {
            log.info("开始向量检索，topK: {}", topK);
            
            // 1. 获取所有向量
            List<KbVectorIndex> allVectors = vectorIndexDao.selectAll();
            if (allVectors.isEmpty()) {
                log.warn("向量库中没有数据");
                return Collections.emptyList();
            }

            // 2. 计算相似度并排序
            List<ScoredChunk> scoredChunks = allVectors.stream()
                    .map(vectorIndex -> {
                        try {
                            float[] vector = objectMapper.readValue(
                                    vectorIndex.getEmbeddingVector(), 
                                    float[].class
                            );
                            double score = cosineSimilarity(queryVector, vector);
                            return new ScoredChunk(vectorIndex, score);
                        } catch (JsonProcessingException e) {
                            log.error("解析向量失败: chunk_id={}", vectorIndex.getChunkId(), e);
                            return null;
                        }
                    })
                    .filter(sc -> sc != null)
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .toList();

            if (scoredChunks.isEmpty()) {
                log.warn("没有找到有效的相似向量");
                return Collections.emptyList();
            }

            // 3. 提取 chunk_id 并查询完整文本
            List<Long> chunkIds = scoredChunks.stream()
                    .map(sc -> sc.getChunk().getChunkId())
                    .collect(Collectors.toList());

            List<KbDocumentChunk> chunks = chunkMapper.selectBatchIds(chunkIds);
            
            // 4. 构建 RagChunk，保持排序顺序和分数
            Map<Long, KbDocumentChunk> chunkMap = chunks.stream()
                    .collect(Collectors.toMap(KbDocumentChunk::getId, c -> c));
            
            Map<Long, Double> scoreMap = scoredChunks.stream()
                    .collect(Collectors.toMap(
                            sc -> sc.getChunk().getChunkId(),
                            ScoredChunk::getScore
                    ));
            
            List<RagChunk> ragChunks = chunkIds.stream()
                    .map(chunkId -> {
                        KbDocumentChunk chunk = chunkMap.get(chunkId);
                        Double score = scoreMap.get(chunkId);
                        if (chunk != null && score != null) {
                            return new RagChunk(
                                    chunk.getTextContent(),
                                    score,
                                    String.valueOf(chunk.getId())
                            );
                        }
                        return null;
                    })
                    .filter(rc -> rc != null)
                    .collect(Collectors.toList());

            log.info("向量检索完成，返回 {} 个结果", ragChunks.size());
            
            // 记录相似度
            for (int i = 0; i < ragChunks.size(); i++) {
                log.debug("结果 #{}: chunk_id={}, score={}",
                        i + 1, 
                        ragChunks.get(i).getChunkId(), 
                        ragChunks.get(i).getScore());
            }
            
            return ragChunks;

        } catch (Exception e) {
            log.error("向量检索失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<List<RagChunk>> batchSearch(List<float[]> queryVectors, int topK) {
        try {
            log.info("开始批量向量检索，查询数量: {}, topK: {}", queryVectors.size(), topK);
            
            // 1. 获取所有向量（一次性加载，避免重复查询）
            List<KbVectorIndex> allVectors = vectorIndexDao.selectAll();
            if (allVectors.isEmpty()) {
                log.warn("向量库中没有数据");
                return Collections.nCopies(queryVectors.size(), Collections.emptyList());
            }

            // 2. 预先解析所有向量，避免重复解析
            List<float[]> parsedVectors = allVectors.stream()
                    .map(vectorIndex -> {
                        try {
                            return objectMapper.readValue(
                                    vectorIndex.getEmbeddingVector(),
                                    float[].class
                            );
                        } catch (JsonProcessingException e) {
                            log.error("解析向量失败: chunk_id={}", vectorIndex.getChunkId(), e);
                            return null;
                        }
                    })
                    .toList();

            // 3. 批量计算相似度
            List<List<RagChunk>> batchResults = queryVectors.stream()
                    .map(queryVector -> {
                        List<ScoredChunk> scoredChunks = new java.util.ArrayList<>();
                        for (int i = 0; i < allVectors.size(); i++) {
                            float[] vector = parsedVectors.get(i);
                            if (vector != null) {
                                double score = cosineSimilarity(queryVector, vector);
                                scoredChunks.add(new ScoredChunk(allVectors.get(i), score));
                            }
                        }
                        
                        // 排序并限制topK
                        List<ScoredChunk> topResults = scoredChunks.stream()
                                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                                .limit(topK)
                                .toList();
                        
                        if (topResults.isEmpty()) {
                            return Collections.<RagChunk>emptyList();
                        }
                        
                        // 提取chunk_id并查询完整文本
                        List<Long> chunkIds = topResults.stream()
                                .map(sc -> sc.getChunk().getChunkId())
                                .collect(Collectors.toList());
                        
                        List<KbDocumentChunk> chunks = chunkMapper.selectBatchIds(chunkIds);
                        Map<Long, KbDocumentChunk> chunkMap = chunks.stream()
                                .collect(Collectors.toMap(KbDocumentChunk::getId, c -> c));
                        
                        Map<Long, Double> scoreMap = topResults.stream()
                                .collect(Collectors.toMap(
                                        sc -> sc.getChunk().getChunkId(),
                                        ScoredChunk::getScore
                                ));
                        
                        return chunkIds.stream()
                                .map(chunkId -> {
                                    KbDocumentChunk chunk = chunkMap.get(chunkId);
                                    Double score = scoreMap.get(chunkId);
                                    if (chunk != null && score != null) {
                                        return new RagChunk(
                                                chunk.getTextContent(),
                                                score,
                                                String.valueOf(chunk.getId())
                                        );
                                    }
                                    return null;
                                })
                                .filter(rc -> rc != null)
                                .collect(Collectors.toList());
                    })
                    .collect(Collectors.toList());

            log.info("批量向量检索完成，返回 {} 组结果", batchResults.size());
            return batchResults;

        } catch (Exception e) {
            log.error("批量向量检索失败", e);
            return Collections.nCopies(queryVectors.size(), Collections.emptyList());
        }
    }

    /**
     * 计算余弦相似度
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 余弦相似度 [-1, 1]，越接近 1 表示越相似
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
