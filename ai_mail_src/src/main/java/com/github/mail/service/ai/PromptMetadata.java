package com.github.mail.service.ai;

public record PromptMetadata(
        String source,
        String promptName,
        String promptLabel,
        Integer promptVersion
) {
}
