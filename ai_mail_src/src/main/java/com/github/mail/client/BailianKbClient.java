package com.github.mail.client;

import com.github.mail.repo.KnowledgeBase.domain.RagChunk;

import java.util.List;

/**
 * 百炼知识库检索客户端
 *
 * @author System
 */
public interface BailianKbClient {

    /**
     * 调用百炼 Retrieve API 检索知识库片段
     *
     * @param query    检索 query
     * @param topK     最大返回数量
     * @param minScore 最低相似度阈值
     * @return 检索到的知识库片段
     */
    List<RagChunk> retrieve(String query, int topK, double minScore);
}
