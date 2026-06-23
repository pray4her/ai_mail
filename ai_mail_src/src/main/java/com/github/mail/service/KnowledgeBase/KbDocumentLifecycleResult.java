package com.github.mail.service.KnowledgeBase;

public record KbDocumentLifecycleResult(
        KbDocumentLifecycleOutcome outcome,
        Long documentId,
        KbDocumentLifecycleStatus status,
        int chunkCount,
        int embeddedCount,
        String message
) {
    public static KbDocumentLifecycleResult success(
            Long documentId,
            KbDocumentLifecycleStatus status,
            int chunkCount,
            int embeddedCount,
            String message
    ) {
        return new KbDocumentLifecycleResult(
                KbDocumentLifecycleOutcome.SUCCESS,
                documentId,
                status,
                chunkCount,
                embeddedCount,
                message
        );
    }

    public static KbDocumentLifecycleResult failure(
            Long documentId,
            KbDocumentLifecycleStatus status,
            String message
    ) {
        return new KbDocumentLifecycleResult(
                KbDocumentLifecycleOutcome.RETRYABLE_FAILURE,
                documentId,
                status,
                0,
                0,
                message
        );
    }

    public static KbDocumentLifecycleResult duplicate(
            Long documentId,
            KbDocumentLifecycleStatus status,
            String message
    ) {
        return new KbDocumentLifecycleResult(
                KbDocumentLifecycleOutcome.DUPLICATE,
                documentId,
                status,
                0,
                0,
                message
        );
    }

    public static KbDocumentLifecycleResult terminalFailure(
            Long documentId,
            KbDocumentLifecycleStatus status,
            String message
    ) {
        return new KbDocumentLifecycleResult(
                KbDocumentLifecycleOutcome.TERMINAL_FAILURE,
                documentId,
                status,
                0,
                0,
                message
        );
    }

    public static KbDocumentLifecycleResult notFound(
            Long documentId,
            KbDocumentLifecycleStatus status,
            String message
    ) {
        return new KbDocumentLifecycleResult(
                KbDocumentLifecycleOutcome.NOT_FOUND,
                documentId,
                status,
                0,
                0,
                message
        );
    }
}
