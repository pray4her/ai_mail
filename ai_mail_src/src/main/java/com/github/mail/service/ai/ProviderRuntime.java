package com.github.mail.service.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

public record ProviderRuntime(
        String providerId,
        String baseUrl,
        String chatModelName,
        String embeddingModelName,
        ChatModel chatModel,
        EmbeddingModel embeddingModel
) {
}
