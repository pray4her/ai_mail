package com.github.mail.service.KnowledgeBase;

import com.github.mail.client.EmbeddingClient;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.Search.ElasticsearchHybridService;
import com.github.mail.service.Search.VectorSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.github.mail.model.config.Properties.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索服务
 * <p>
 * 核心流程：
 * 1. 对查询文本进行临时 embedding（不落库）
 * 2. 混合检索 (Hybrid Search):
 * - 优先使用 ES (knn + BM25)
 * - 降级到 MySQL (Hybrid Search)
 * - 最后降级到纯向量检索
 * 3. 返回相关的知识库上下文
 * <p>
 * 设计原则：
 * - 邮件内容仅做临时 embedding，不存储
 * - 分层降级检索策略，保障可用性
 * - MySQL 存储 chunk 文本和向量
 *
 * @author Asteries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorSearch vectorStore;
    private final ElasticsearchHybridService esHybridService;
    private final EmbeddingClient embeddingClient;

    private final RagProperties ragProperties;

    /**
     * 单条检索：对外提供的标准接口
     */
    public List<RagChunk> retrieveRagChunks(String queryText, int topK, double minScore) {
        if (queryText == null || queryText.isBlank()) {
            return new ArrayList<>();
        }

        float[] embedding = embeddingClient.embed(queryText);
        return doSearch(queryText, embedding, topK, minScore);
    }

    /**
     * 批量检索：对外提供的标准接口
     * 利用 embedBatch 减少网络 IO 开销
     */
    public List<List<RagChunk>> batchRetrieveRagChunks(List<String> queryTexts, int topK, double minScore) {
        if (queryTexts == null || queryTexts.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 批量 Embedding (性能关键点)
        log.debug("开始批量 Embedding，条数: {}", queryTexts.size());
        List<float[]> embeddings = embeddingClient.embedBatch(queryTexts);

        // 2. 依次执行检索逻辑
        List<List<RagChunk>> results = new ArrayList<>();
        for (int i = 0; i < queryTexts.size(); i++) {
            results.add(doSearch(queryTexts.get(i), embeddings.get(i), topK, minScore));
        }
        return results;
    }

    /**
     * 核心内部方法：封装分层检索与降级逻辑
     * 确保单条和批量查询遵循完全一致的业务规则
     */
    private List<RagChunk> doSearch(String text, float[] embedding, int k, double minScore) {
        List<RagChunk> results = new ArrayList<>();

        // 1. 尝试 ES 混合检索
        if (ragProperties.isUseElasticsearch()) {
            try {
                results = esHybridService.hybridSearch(text, embedding, k);
                if (!results.isEmpty()) {
                    return filterByScore(results, minScore);
                }
            } catch (Exception e) {
                log.error("ES 检索异常，准备降级到纯向量检索: {}", e.getMessage());
            }
        }

        // 2. 降级：纯向量检索 //TODO：纯向量检索为早期架构，要求向量数据存数据库
//        try {
//            results = vectorStore.search(embedding, k);
//        } catch (Exception e) {
//            log.error("向量检索失败: ", e);
//        }

        return filterByScore(results, minScore);
    }

    /**
     * 统一分数过滤
     */
    private List<RagChunk> filterByScore(List<RagChunk> chunks, double minScore) {
        return chunks.stream()
                .filter(c -> c.getScore() >= minScore)
                .collect(Collectors.toList());
    }

    // --- 简化调用重载 ---

    public List<RagChunk> retrieveRagChunks(String queryText) {
        return retrieveRagChunks(queryText, ragProperties.getDefaultTopk(), ragProperties.getDefaultMinScore());
    }

    public List<List<RagChunk>> batchRetrieveRagChunks(List<String> queryTexts) {
        return batchRetrieveRagChunks(queryTexts, ragProperties.getDefaultTopk(), ragProperties.getDefaultMinScore());
    }
}


