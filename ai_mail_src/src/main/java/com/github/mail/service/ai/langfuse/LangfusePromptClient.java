package com.github.mail.service.ai.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.service.ai.dto.LangfusePromptCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LangfusePromptClient {

    private final LangfuseProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public JsonNode listPrompts(String name,
                                String label,
                                String tag,
                                Integer page,
                                Integer limit) {
        try {
            return webClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/public/v2/prompts")
                            .queryParamIfPresent("name", Optional.ofNullable(name))
                            .queryParamIfPresent("label", Optional.ofNullable(label))
                            .queryParamIfPresent("tag", Optional.ofNullable(tag))
                            .queryParamIfPresent("page", Optional.ofNullable(page))
                            .queryParamIfPresent("limit", Optional.ofNullable(limit))
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw mapResponseException("查询 Prompt 列表失败", exception);
        } catch (Exception exception) {
            throw new LangfusePromptOperationException("查询 Prompt 列表失败", null, exception);
        }
    }

    public JsonNode getPrompt(String name, Integer version, String label, Boolean resolve) {
        try {
            return webClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/public/v2/prompts/{name}")
                            .queryParamIfPresent("version", Optional.ofNullable(version))
                            .queryParamIfPresent("label", Optional.ofNullable(label))
                            .queryParamIfPresent("resolve", Optional.ofNullable(resolve))
                            .build(name))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw mapResponseException("获取 Prompt 失败", exception);
        } catch (Exception exception) {
            throw new LangfusePromptOperationException("获取 Prompt 失败", null, exception);
        }
    }

    public JsonNode createPrompt(LangfusePromptCreateRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", request.name());
        body.put("type", request.type().toLowerCase());
        body.set("prompt", request.prompt());
        if (request.config() != null && !request.config().isNull()) {
            body.set("config", request.config());
        }
        if (request.labels() != null) {
            body.set("labels", objectMapper.valueToTree(request.labels()));
        }
        if (request.tags() != null) {
            body.set("tags", objectMapper.valueToTree(request.tags()));
        }
        if (request.commitMessage() != null && !request.commitMessage().isBlank()) {
            body.put("commitMessage", request.commitMessage());
        }

        try {
            return webClient().post()
                    .uri("/api/public/v2/prompts")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw mapResponseException("创建 Prompt 失败", exception);
        } catch (Exception exception) {
            throw new LangfusePromptOperationException("创建 Prompt 失败", null, exception);
        }
    }

    public JsonNode updatePromptLabels(String name, Integer version, List<String> newLabels) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("newLabels", objectMapper.valueToTree(newLabels));

        try {
            return webClient().patch()
                    .uri("/api/public/v2/prompts/{name}/versions/{version}", name, version)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw mapResponseException("更新 Prompt 标签失败", exception);
        } catch (Exception exception) {
            throw new LangfusePromptOperationException("更新 Prompt 标签失败", null, exception);
        }
    }

    public void deletePrompt(String name, Integer version, String label) {
        try {
            webClient().delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/public/v2/prompts/{name}")
                            .queryParamIfPresent("version", Optional.ofNullable(version))
                            .queryParamIfPresent("label", Optional.ofNullable(label))
                            .build(name))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException exception) {
            throw mapResponseException("删除 Prompt 失败", exception);
        } catch (Exception exception) {
            throw new LangfusePromptOperationException("删除 Prompt 失败", null, exception);
        }
    }

    private WebClient webClient() {
        ensureConfigured();
        return webClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(properties.getUrl()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.getPublicKey(), properties.getSecretKey()))
                .build();
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Langfuse 未启用，无法管理 Prompt");
        }
        if (properties.getPublicKey() == null || properties.getPublicKey().isBlank()
                || properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            throw new IllegalStateException("Langfuse 凭据未配置完整，无法管理 Prompt");
        }
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://cloud.langfuse.com";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private LangfusePromptOperationException mapResponseException(String message, WebClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        String finalMessage = responseBody == null || responseBody.isBlank()
                ? message
                : message + ": " + responseBody;
        return new LangfusePromptOperationException(finalMessage, exception.getStatusCode().value(), exception);
    }
}
