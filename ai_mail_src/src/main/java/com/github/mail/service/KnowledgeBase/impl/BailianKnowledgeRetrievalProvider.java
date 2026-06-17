package com.github.mail.service.KnowledgeBase.impl;

import com.github.mail.client.BailianKbClient;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.KnowledgeBase.KnowledgeRetrievalProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 百炼知识库 Retrieve API Provider
 *
 * @author System
 */
@Slf4j
@Service("bailianKnowledgeRetrievalProvider")
@RequiredArgsConstructor
public class BailianKnowledgeRetrievalProvider implements KnowledgeRetrievalProvider {

    private final BailianKbClient bailianKbClient;

    @Override
    public List<RagChunk> retrieve(String query, int topK, double minScore) {
        return bailianKbClient.retrieve(query, topK, minScore);
    }

    @Override
    public List<List<RagChunk>> batchRetrieve(List<String> queries, int topK, double minScore) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("百炼知识库顺序批量检索，条数: {}", queries.size());
        List<List<RagChunk>> results = new ArrayList<>(queries.size());
        for (String query : queries) {
            results.add(bailianKbClient.retrieve(query, topK, minScore));
        }
        return results;
    }
}
