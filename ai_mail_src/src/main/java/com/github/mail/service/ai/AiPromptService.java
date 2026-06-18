package com.github.mail.service.ai;

public interface AiPromptService {

    PreparedPrompt preparePrompt(AiGenerationRequest request);
}
