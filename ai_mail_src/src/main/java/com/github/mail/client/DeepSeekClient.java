package com.github.mail.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.model.config.DeepSeekConfig;
import com.github.mail.service.Config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek API 客户端（同步版本）
 * 使用 WebClient + .block() 实现同步调用
 *
 * @author Asteries
 */
@Slf4j
@Component
public class DeepSeekClient {


    private final WebClient webClient;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(ConfigService configService,
                          @Qualifier("deepSeekWebClient")
                          WebClient webClient,
                          ObjectMapper objectMapper) {

        this.configService = configService;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 动态获取AI配置
     *
     * @return
     */
    private DeepSeekConfig aiConfig() {
        return configService.getConfig().getDeepseek();
    }


    //通过已有prompt构建deepSeekApi请求
    public String generateTemplateByPrompt(String prompt) {

        DeepSeekConfig properties = aiConfig();

        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DeepSeek API 未配置，请确定使用本地模型");

        }

        try {
            String apiUrl = normalizeChatCompletionsUrl(properties.getApiUrl());
            Map<String, Object> request = buildRequest(prompt);

            String response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractContent(response);

        } catch (WebClientResponseException e) {
            log.error("DeepSeek API 调用失败: status={}, url={}, model={}, bodyLen={}",
                    e.getStatusCode().value(),
                    safeUrl(properties.getApiUrl()),
                    properties.getModel(),
                    e.getResponseBodyAsString() == null ? 0 : e.getResponseBodyAsString().length(),
                    e);
            return "失败";
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败: url={}, model={}", safeUrl(properties.getApiUrl()), properties.getModel(), e);
            return "失败";
        }


    }


    /**
     * 构建 DeepSeek API 请求
     */
    private Map<String, Object> buildRequest(String prompt) {

        DeepSeekConfig properties = aiConfig();

        Map<String, Object> request = new HashMap<>();
        String model = properties.getModel();
        request.put("model", model);
        request.put("messages", List.of(
                Map.of("role", "system", "content", "你是一个专业的邮件回复助手。根据邮件意图、内容和背景知识生成简洁、专业、准确的邮件回复模板。生成的回复应该清晰、友好和有帮助。"),
                Map.of("role", "user", "content", prompt)
        ));
        request.put("temperature", 0.7);
        request.put("max_tokens", 1000);
        return request;
    }

    /**
     * 从 DeepSeek 响应中提取内容
     */
    private String extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("DeepSeek response has no choices, len={}", response == null ? 0 : response.length());
                return "";
            }

            JsonNode message = choices.get(0).path("message");
            if (message.isMissingNode()) {
                log.warn("DeepSeek response has no message, len={}", response == null ? 0 : response.length());
                return "";
            }

            return message.path("content").asText("");
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败", e);
            return "";
        }
    }

    private String normalizeChatCompletionsUrl(String apiUrl) {
        if (apiUrl == null) {
            return "";
        }
        String trimmed = apiUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/compatible-mode/v1") || trimmed.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        return trimmed;
    }

    private String safeUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("([?&](?:api_key|key|token)=)[^&]+", "$1***");
    }

}
