package com.github.mail.service.KnowledgeBase;

public enum KbDocumentLifecycleStatus {
    UPLOADING(0),
    PARSED(1),
    VECTORIZED(2),
    FAILED(9);

    private final int code;

    KbDocumentLifecycleStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static KbDocumentLifecycleStatus fromCode(Integer code) {
        if (code == null) {
            return FAILED;
        }
        for (KbDocumentLifecycleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return FAILED;
    }
}
