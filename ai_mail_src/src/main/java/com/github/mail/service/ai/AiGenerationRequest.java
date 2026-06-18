package com.github.mail.service.ai;

import com.github.mail.repo.KnowledgeBase.domain.RagChunk;

import java.util.List;
import java.util.Map;

public record AiGenerationRequest(
        String providerId,
        String userQuery,
        List<RagChunk> ragChunks,
        Map<String, Object> traceMetadata
) {

    public AiGenerationRequest {
        ragChunks = ragChunks == null ? List.of() : List.copyOf(ragChunks);
        traceMetadata = traceMetadata == null ? Map.of() : Map.copyOf(traceMetadata);
    }
}
