package com.github.mail.service.KnowledgeBase;

public enum KbDocumentLifecycleOutcome {
    SUCCESS,
    DUPLICATE,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE,
    NOT_FOUND
}
