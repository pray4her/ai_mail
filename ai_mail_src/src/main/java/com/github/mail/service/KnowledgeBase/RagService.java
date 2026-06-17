package com.github.mail.service.KnowledgeBase;

import com.github.mail.model.config.Properties.RagProperties;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.Config.ConfigService;
import com.github.mail.service.KnowledgeBase.impl.BailianKnowledgeRetrievalProvider;
import com.github.mail.service.KnowledgeBase.impl.LocalEsKnowledgeRetrievalProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索服务：按配置路由到本地 ES 或百炼知识库
 *
 * @author Asteries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final String PROVIDER_BAILIAN = "bailian";

    private final LocalEsKnowledgeRetrievalProvider localProvider;
    private final BailianKnowledgeRetrievalProvider bailianProvider;
    private final ConfigService configService;
    private final RagProperties ragProperties;

    public List<RagChunk> retrieveRagChunks(String queryText, int topK, double minScore) {
        if (queryText == null || queryText.isBlank()) {
            return new ArrayList<>();
        }
        return resolveProvider().retrieve(queryText, topK, minScore);
    }

    public List<List<RagChunk>> batchRetrieveRagChunks(List<String> queryTexts, int topK, double minScore) {
        if (queryTexts == null || queryTexts.isEmpty()) {
            return new ArrayList<>();
        }
        return resolveProvider().batchRetrieve(queryTexts, topK, minScore);
    }

    public List<RagChunk> retrieveRagChunks(String queryText) {
        return retrieveRagChunks(queryText, ragProperties.getDefaultTopk(), ragProperties.getDefaultMinScore());
    }

    public List<List<RagChunk>> batchRetrieveRagChunks(List<String> queryTexts) {
        return batchRetrieveRagChunks(queryTexts, ragProperties.getDefaultTopk(), ragProperties.getDefaultMinScore());
    }

    private KnowledgeRetrievalProvider resolveProvider() {
        String provider = configService.getConfig().getMail().getRag().getProvider();
        if (PROVIDER_BAILIAN.equalsIgnoreCase(provider)) {
            log.debug("RAG 检索使用百炼知识库");
            return bailianProvider;
        }
        log.debug("RAG 检索使用本地 ES");
        return localProvider;
    }
}
