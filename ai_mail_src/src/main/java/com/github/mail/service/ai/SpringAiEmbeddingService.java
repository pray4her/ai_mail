package com.github.mail.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiEmbeddingService implements AiEmbeddingService {

    private final ChatProviderRegistry providerRegistry;

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        return providerRegistry.getDefaultEmbeddingProvider().embeddingModel().embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        return providerRegistry.getDefaultEmbeddingProvider().embeddingModel().embed(texts);
    }

    @Override
    public String currentModel() {
        return providerRegistry.getDefaultEmbeddingProvider().embeddingModelName();
    }
}
