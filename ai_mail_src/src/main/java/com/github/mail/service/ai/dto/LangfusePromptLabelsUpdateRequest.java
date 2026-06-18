package com.github.mail.service.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record LangfusePromptLabelsUpdateRequest(
        @NotNull Integer version,
        @NotNull List<String> newLabels
) {
}
