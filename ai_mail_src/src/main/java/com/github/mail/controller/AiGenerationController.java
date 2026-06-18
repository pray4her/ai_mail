package com.github.mail.controller;

import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.Ai.dto.AiGenerationPreviewRequest;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.KnowledgeBase.RagService;
import com.github.mail.service.ai.AiGenerationRequest;
import com.github.mail.service.ai.AiGenerationResult;
import com.github.mail.service.ai.AiGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/generation")
@RequiredArgsConstructor
public class AiGenerationController {

    private final AiGenerationService aiGenerationService;
    private final RagService ragService;
    private final MailConfig mailConfig;

    @PostMapping
    public AiGenerationResult generate(@RequestBody AiGenerationPreviewRequest request) {
        return aiGenerationService.generate(buildRequest(request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody AiGenerationPreviewRequest request) {
        return aiGenerationService.stream(buildRequest(request))
                .map(chunk -> ServerSentEvent.builder(chunk).build());
    }

    private AiGenerationRequest buildRequest(AiGenerationPreviewRequest request) {
        List<RagChunk> ragChunks = List.of();
        if (Boolean.TRUE.equals(request.getUseRag())) {
            ragChunks = ragService.retrieveRagChunks(
                    request.getUserQuery(),
                    request.getTopK() == null ? mailConfig.getRag().getTopK() : request.getTopK(),
                    request.getMinScore() == null ? mailConfig.getRag().getMinScore() : request.getMinScore()
            );
        }
        return new AiGenerationRequest(
                request.getProviderId(),
                request.getUserQuery(),
                ragChunks,
                Map.of("entrypoint", "preview-api")
        );
    }
}
