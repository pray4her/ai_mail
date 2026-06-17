package com.github.mail.service.KnowledgeBase.impl;

import com.github.mail.client.EmbeddingClient;
import com.github.mail.model.config.Properties.RagProperties;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.KnowledgeBase.KnowledgeRetrievalProvider;
import com.github.mail.service.Search.ElasticsearchHybridService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 本地 ES 混合检索 Provider
 *
 * @author System
 */
@Slf4j
@Service("localEsKnowledgeRetrievalProvider")
@RequiredArgsConstructor
public class LocalEsKnowledgeRetrievalProvider implements KnowledgeRetrievalProvider {

    private final ElasticsearchHybridService esHybridService;
    private final EmbeddingClient embeddingClient;
    private final RagProperties ragProperties;

    @Override
    public List<RagChunk> retrieve(String query, int topK, double minScore) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        float[] embedding = embeddingClient.embed(query);
        return doSearch(query, embedding, topK, minScore);
    }

    @Override
    public List<List<RagChunk>> batchRetrieve(List<String> queries, int topK, double minScore) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("本地 ES 批量 Embedding，条数: {}", queries.size());
        List<float[]> embeddings = embeddingClient.embedBatch(queries);

        List<List<RagChunk>> results = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            results.add(doSearch(queries.get(i), embeddings.get(i), topK, minScore));
        }
        return results;
    }

    private List<RagChunk> doSearch(String text, float[] embedding, int topK, double minScore) {
        List<RagChunk> results = Collections.emptyList();

        if (ragProperties.isUseElasticsearch()) {
            try {
                results = esHybridService.hybridSearch(text, embedding, topK);
            } catch (Exception e) {
                log.error("ES 检索异常: {}", e.getMessage());
            }
        }

        return filterByScore(results, minScore);
    }

    private List<RagChunk> filterByScore(List<RagChunk> chunks, double minScore) {
        return chunks.stream()
                .filter(c -> c.getScore() >= minScore)
                .collect(Collectors.toList());
    }
}
