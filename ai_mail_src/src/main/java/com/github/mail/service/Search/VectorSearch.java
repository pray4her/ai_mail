package com.github.mail.service.Search;

import com.github.mail.repo.KnowledgeBase.domain.RagChunk;

import java.util.List;

/**
 * 向量搜索接口 TODO：目前架构已经不需要，但mysql数据库向量检索测试通过，未来如果想废弃ES，可以启用当前模块
 * 
 * @author Aster
 * @date 2025/12/29
 */
public interface VectorSearch {
    
    /**
     * 搜索最相关的知识库片段
     * 
     * @param queryVector 查询向量
     * @param topK        返回前 K 个结果
     * @return 带有相似度分数的 RagChunk 列表
     */
    List<RagChunk> search(float[] queryVector, int topK);

    /**
     * 批量搜索最相关的知识库片段
     */
    List<List<RagChunk>> batchSearch(List<float[]> queryVectors, int topK);
}
