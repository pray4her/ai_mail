package com.github.mail.service.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record LangfusePromptListItem(
        String name,
        String type,
        List<Integer> versions,
        List<String> labels,
        List<String> tags,
        String lastUpdatedAt,
        JsonNode lastConfig
) {
}
