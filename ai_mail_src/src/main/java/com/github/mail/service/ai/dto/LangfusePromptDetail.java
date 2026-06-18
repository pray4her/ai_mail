package com.github.mail.service.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record LangfusePromptDetail(
        String name,
        String type,
        Integer version,
        JsonNode prompt,
        JsonNode config,
        List<String> labels,
        List<String> tags,
        String commitMessage,
        JsonNode resolutionGraph
) {
}
