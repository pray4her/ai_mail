package com.github.mail.service.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record LangfusePromptListResponse(
        List<LangfusePromptListItem> data,
        JsonNode meta
) {
}
