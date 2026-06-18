package com.github.mail.service.ai.dto;

import java.util.List;

public record LangfusePromptVariablesResponse(
        String promptName,
        String runtimeLabel,
        List<String> reservedVariables,
        List<String> optionalVariables
) {
}
