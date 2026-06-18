package com.github.mail.service.ai;

public record AiGenerationResult(
        String content,
        String providerId,
        String model,
        PromptMetadata promptMetadata,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        String traceId
) {
}
