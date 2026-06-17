package com.github.mail.service.KnowledgeBase;

import com.github.mail.repo.KnowledgeBase.domain.RagChunk;

import java.util.List;

/**
 * 知识库检索 Provider 抽象
 *
 * @author System
 */
public interface KnowledgeRetrievalProvider {

    List<RagChunk> retrieve(String query, int topK, double minScore);

    List<List<RagChunk>> batchRetrieve(List<String> queries, int topK, double minScore);
}
