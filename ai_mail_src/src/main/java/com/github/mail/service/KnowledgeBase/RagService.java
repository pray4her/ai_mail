package com.github.mail.service.KnowledgeBase;

import com.github.mail.model.config.MailConfig;
import com.github.mail.model.config.Properties.RagProperties;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private static final String PROVIDER_LOCAL = "local";
    private static final String LOCAL_PROVIDER_BEAN = "localEsKnowledgeRetrievalProvider";
    private static final String BAILIAN_PROVIDER_BEAN = "bailianKnowledgeRetrievalProvider";

    private final Map<String, KnowledgeRetrievalProvider> providers;
    private final MailConfig mailConfig;
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
        String provider = mailConfig.getRag().getProvider();
        if (PROVIDER_BAILIAN.equalsIgnoreCase(provider)) {
            log.debug("RAG 检索使用百炼知识库");
            return getProvider(PROVIDER_BAILIAN);
        }
        log.debug("RAG 检索使用本地 ES");
        return getProvider(PROVIDER_LOCAL);
    }

    private KnowledgeRetrievalProvider getProvider(String providerKey) {
        String beanName = PROVIDER_BAILIAN.equalsIgnoreCase(providerKey)
                ? BAILIAN_PROVIDER_BEAN
                : LOCAL_PROVIDER_BEAN;
        KnowledgeRetrievalProvider provider = providers.get(beanName);
        if (provider == null) {
            throw new IllegalStateException("未找到 RAG Provider: " + providerKey);
        }
        return provider;
    }
}
