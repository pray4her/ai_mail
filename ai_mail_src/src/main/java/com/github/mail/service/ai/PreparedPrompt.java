package com.github.mail.service.ai;

import org.springframework.ai.chat.prompt.Prompt;

public record PreparedPrompt(
        Prompt prompt,
        PromptMetadata metadata
) {
}
