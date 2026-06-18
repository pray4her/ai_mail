package com.github.mail.service.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record LangfusePromptCreateRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotNull JsonNode prompt,
        JsonNode config,
        List<String> labels,
        List<String> tags,
        String commitMessage
) {
}
