package com.github.mail.service.ai.langfuse;

public class LangfusePromptOperationException extends RuntimeException {

    private final Integer statusCode;

    public LangfusePromptOperationException(String message) {
        this(message, null, null);
    }

    public LangfusePromptOperationException(String message, Integer statusCode) {
        this(message, statusCode, null);
    }

    public LangfusePromptOperationException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
