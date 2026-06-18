package com.github.mail.service.ai;

import reactor.core.publisher.Flux;

public interface AiGenerationService {

    AiGenerationResult generate(AiGenerationRequest request);

    Flux<String> stream(AiGenerationRequest request);
}
