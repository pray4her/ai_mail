package com.github.mail.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Validated
@ConfigurationProperties(prefix = "app.ai")
public class AppAiProperties {

    @NotBlank
    private String defaultProvider = "default";

    @NotBlank
    private String defaultEmbeddingProvider = "default";

    @NotBlank
    private String fallbackSystemPrompt = "你是一个专业的邮件回复助手。请基于规则、策略、知识库上下文和用户邮件内容生成专业、准确、礼貌的回复。";

    @Valid
    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Data
    public static class Provider {

        private boolean enabled = true;

        @NotBlank
        private String baseUrl;

        @NotBlank
        private String apiKey;

        @NotBlank
        private String chatModel;

        @NotBlank
        private String embeddingModel;

        private int embeddingDimensions = 2048;

        private Double temperature;

        private Integer maxTokens;
    }
}
