package com.github.mail.service.ai;

import com.github.mail.repo.KnowledgeBase.domain.RagChunk;

import java.util.List;
import java.util.Map;

public record AiGenerationRequest(
        String providerId,
        String userQuery,
        List<RagChunk> ragChunks,
        List<AiInputAttachment> attachments,
        Map<String, Object> traceMetadata,
        boolean useNativeAttachments
) {

    public AiGenerationRequest {
        ragChunks = ragChunks == null ? List.of() : List.copyOf(ragChunks);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        traceMetadata = traceMetadata == null ? Map.of() : Map.copyOf(traceMetadata);
    }

    public AiGenerationRequest(String providerId,
                               String userQuery,
                               List<RagChunk> ragChunks,
                               Map<String, Object> traceMetadata) {
        this(providerId, userQuery, ragChunks, List.of(), traceMetadata, false);
    }
}
