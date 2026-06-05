package com.github.mail.service.Search;

import com.github.mail.repo.KnowledgeBase.domain.RagChunk;

import java.util.List;

/**
 * Elasticsearch 混合检索服务
 * <p>
 * 基于 ES 的 Hybrid Search:
 * 1. knn 向量召回 (Top-300)
 * 2. BM25 关键词重排
 * 3. 分数融合: final = bm25 × 1.0 + knn × 0.2
 *
 * @author Aster
 * @date 2025/12/30
 */
public interface ElasticsearchHybridService {

    /**
     * ES 混合检索
     * <p>
     * 使用 ES script_score 实现分数融合
     *
     * @param queryText   查询文本
     * @param queryVector 查询向量
     * @param topK        最终返回数量
     * @return 混合检索结果
     */
    List<RagChunk> hybridSearch(String queryText, float[] queryVector, int topK);

    /**
     * 混合检索, 并且使用并行召回 //TODO：双路召回模式暂未使用
     *
     * @param queryText   查询文本
     * @param queryVector 查询向量
     * @param topK        最终返回数量
     * @return 混合检索结果
     */
    List<RagChunk> hybridSearchParallelRecall(String queryText, float[] queryVector, int topK);



}
