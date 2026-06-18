package com.github.mail.repo.Ai.dto;

import lombok.Data;

@Data
public class AiGenerationPreviewRequest {

    private String providerId;

    private String userQuery;

    private Boolean useRag = Boolean.TRUE;

    private Integer topK;

    private Double minScore;
}
