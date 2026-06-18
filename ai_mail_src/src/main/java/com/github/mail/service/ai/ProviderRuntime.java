package com.github.mail.service.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public record ProviderRuntime(
        String providerId,
        String baseUrl,
        String chatModelName,
        String embeddingModelName,
        OpenAiApi openAiApi,
        OpenAiChatOptions defaultChatOptions,
        ChatModel chatModel,
        EmbeddingModel embeddingModel
) {
}
