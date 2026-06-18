package com.github.mail.service.ai.langfuse;

import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.service.ai.AiGenerationRequest;
import com.github.mail.service.ai.PreparedPrompt;
import com.github.mail.service.ai.PromptMetadata;
import com.github.mail.service.ai.ProviderRuntime;
import com.langfuse.client.resources.ingestion.requests.IngestionRequest;
import com.langfuse.client.resources.ingestion.types.CreateGenerationBody;
import com.langfuse.client.resources.ingestion.types.CreateGenerationEvent;
import com.langfuse.client.resources.ingestion.types.IngestionEvent;
import com.langfuse.client.resources.ingestion.types.IngestionResponse;
import com.langfuse.client.resources.ingestion.types.IngestionUsage;
import com.langfuse.client.resources.ingestion.types.OpenAiUsage;
import com.langfuse.client.resources.ingestion.types.TraceBody;
import com.langfuse.client.resources.ingestion.types.TraceEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangfuseTracingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Set<String> RESERVED_TRACE_METADATA_KEYS = Set.of("userId", "sessionId", "tags");
    private static final Set<String> HIGH_RISK_METADATA_KEYS = Set.of(
            "from",
            "subject",
            "userQuery",
            "prompt",
            "promptText",
            "response",
            "outputContent",
            "htmlBody",
            "textBody",
            "rawBody"
    );
    private static final int INPUT_PREVIEW_LIMIT = 500;
    private static final int OUTPUT_PREVIEW_LIMIT = 500;
    private static final int METADATA_TEXT_LIMIT = 160;

    private final LangfuseClientFactory clientFactory;
    private final LangfuseProperties properties;
    private final MeterRegistry meterRegistry;

    public String newTraceId() {
        return UUID.randomUUID().toString();
    }

    public void recordSuccess(String traceId,
                              AiGenerationRequest request,
                              PreparedPrompt preparedPrompt,
                              ProviderRuntime runtime,
                              String output,
                              Usage usage,
                              long durationNanos,
                              boolean streaming) {
        recordMetrics(runtime, durationNanos, true, streaming);
        clientFactory.getClient().ifPresent(client -> {
            OffsetDateTime endTime = OffsetDateTime.now();
            OffsetDateTime startTime = endTime.minusNanos(durationNanos);
            TraceEnvelope envelope = buildEnvelope(request, preparedPrompt, runtime, output, usage, streaming, null);
            submit(client, List.of(
                    IngestionEvent.traceCreate(buildTraceEvent(traceId, startTime, endTime, envelope)),
                    IngestionEvent.generationCreate(buildGenerationEvent(traceId, startTime, endTime, envelope, usage, null))
            ));
        });
    }

    public void recordFailure(String traceId,
                              AiGenerationRequest request,
                              PreparedPrompt preparedPrompt,
                              ProviderRuntime runtime,
                              Throwable error,
                              long durationNanos,
                              boolean streaming) {
        recordMetrics(runtime, durationNanos, false, streaming);
        clientFactory.getClient().ifPresent(client -> {
            OffsetDateTime endTime = OffsetDateTime.now();
            OffsetDateTime startTime = endTime.minusNanos(durationNanos);
            String errorMessage = error == null ? "unknown error" : error.getMessage();
            TraceEnvelope envelope = buildEnvelope(request, preparedPrompt, runtime, "", null, streaming, errorMessage);
            submit(client, List.of(
                    IngestionEvent.traceCreate(buildTraceEvent(traceId, startTime, endTime, envelope)),
                    IngestionEvent.generationCreate(buildGenerationEvent(traceId, startTime, endTime, envelope, null, errorMessage))
            ));
        });
    }

    private TraceEvent buildTraceEvent(String traceId,
                                       OffsetDateTime startTime,
                                       OffsetDateTime endTime,
                                       TraceEnvelope envelope) {
        TraceBody.Builder bodyBuilder = TraceBody.builder()
                .id(traceId)
                .timestamp(startTime)
                .name(properties.getTraceName())
                .input(envelope.traceInput())
                .output(envelope.traceOutput())
                .metadata(envelope.traceMetadata())
                .tags(envelope.tags())
                .environment(properties.getEnvironment())
                .public_(Boolean.FALSE);
        if (envelope.userId() != null && !envelope.userId().isBlank()) {
            bodyBuilder.userId(envelope.userId());
        }
        if (envelope.sessionId() != null && !envelope.sessionId().isBlank()) {
            bodyBuilder.sessionId(envelope.sessionId());
        }
        return TraceEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(endTime.toString())
                .body(bodyBuilder.build())
                .build();
    }

    private CreateGenerationEvent buildGenerationEvent(String traceId,
                                                       OffsetDateTime startTime,
                                                       OffsetDateTime endTime,
                                                       TraceEnvelope envelope,
                                                       Usage usage,
                                                       String errorMessage) {
        PromptMetadata promptMetadata = envelope.promptMetadata();
        CreateGenerationBody.Builder bodyBuilder = CreateGenerationBody.builder()
                .id(traceId + "-generation")
                .traceId(traceId)
                .name(resolveGenerationName())
                .startTime(startTime)
                .endTime(endTime)
                .input(envelope.generationInput())
                .output(envelope.generationOutput())
                .model(envelope.model())
                .environment(properties.getEnvironment())
                .metadata(envelope.generationMetadata());
        if (promptMetadata.promptName() != null && !promptMetadata.promptName().isBlank()) {
            bodyBuilder.promptName(promptMetadata.promptName());
        }
        if (promptMetadata.promptVersion() != null) {
            bodyBuilder.promptVersion(promptMetadata.promptVersion());
        }
        if (usage != null) {
            bodyBuilder.usage(toUsage(usage));
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            bodyBuilder.statusMessage(sanitizeText(errorMessage, METADATA_TEXT_LIMIT));
        }
        return CreateGenerationEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(endTime.toString())
                .body(bodyBuilder.build())
                .build();
    }

    private void submit(com.langfuse.client.LangfuseClient client, List<IngestionEvent> batch) {
        IngestionRequest request = IngestionRequest.builder()
                .batch(batch)
                .metadata(Map.of("sdk", "langfuse-java", "environment", properties.getEnvironment()))
                .build();
        IngestionResponse response = client.ingestion().batch(request);
        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            log.warn("Langfuse ingestion 存在错误: count={}", response.getErrors().size());
        }
    }

    private IngestionUsage toUsage(Usage usage) {
        if (usage == null) {
            return null;
        }
        return IngestionUsage.of(
                OpenAiUsage.builder()
                        .promptTokens(usage.getPromptTokens())
                        .completionTokens(usage.getCompletionTokens())
                        .totalTokens(usage.getTotalTokens())
                        .build()
        );
    }

    private TraceEnvelope buildEnvelope(AiGenerationRequest request,
                                        PreparedPrompt preparedPrompt,
                                        ProviderRuntime runtime,
                                        String output,
                                        Usage usage,
                                        boolean streaming,
                                        String errorMessage) {
        PromptMetadata promptMetadata = preparedPrompt == null
                ? new PromptMetadata("unknown", properties.getPromptName(), properties.getPromptLabel(), properties.getPromptVersion())
                : preparedPrompt.metadata();
        String safeInputPreview = sanitizeText(request.userQuery(), INPUT_PREVIEW_LIMIT);
        String safeOutputPreview = sanitizeText(output, OUTPUT_PREVIEW_LIMIT);

        Map<String, Object> traceInput = new LinkedHashMap<>();
        traceInput.put("userQuery", safeInputPreview);
        traceInput.put("ragChunkCount", request.ragChunks().size());
        traceInput.put("attachmentCount", request.attachments().size());
        traceInput.put("useNativeAttachments", request.useNativeAttachments());

        Map<String, Object> traceOutput = new LinkedHashMap<>();
        traceOutput.put("contentPreview", safeOutputPreview);
        traceOutput.put("contentLength", output == null ? 0 : output.length());
        if (usage != null) {
            traceOutput.put("promptTokens", usage.getPromptTokens());
            traceOutput.put("completionTokens", usage.getCompletionTokens());
            traceOutput.put("totalTokens", usage.getTotalTokens());
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            traceOutput.put("error", sanitizeText(errorMessage, METADATA_TEXT_LIMIT));
        }

        Map<String, Object> generationInput = new LinkedHashMap<>(traceInput);
        generationInput.put("promptSource", promptMetadata.source());
        generationInput.put("promptName", promptMetadata.promptName());
        generationInput.put("promptVersion", promptMetadata.promptVersion());

        Map<String, Object> traceMetadata = buildTraceMetadata(promptMetadata, runtime, request.traceMetadata(), usage, streaming, errorMessage);
        Map<String, Object> generationMetadata = new LinkedHashMap<>(traceMetadata);
        generationMetadata.put("observationType", "generation");

        return new TraceEnvelope(
                traceInput,
                traceOutput,
                generationInput,
                new LinkedHashMap<>(traceOutput),
                traceMetadata,
                generationMetadata,
                extractTraceString(request.traceMetadata(), "userId"),
                extractTraceString(request.traceMetadata(), "sessionId"),
                buildTags(runtime, promptMetadata, request.traceMetadata(), streaming),
                runtime.chatModelName(),
                promptMetadata
        );
    }

    private Map<String, Object> buildTraceMetadata(PromptMetadata promptMetadata,
                                                   ProviderRuntime runtime,
                                                   Map<String, Object> traceMetadata,
                                                   Usage usage,
                                                   boolean streaming,
                                                   String errorMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("providerId", runtime.providerId());
        metadata.put("baseUrl", sanitizeText(runtime.baseUrl(), METADATA_TEXT_LIMIT));
        metadata.put("chatModel", runtime.chatModelName());
        metadata.put("embeddingModel", runtime.embeddingModelName());
        metadata.put("promptSource", promptMetadata.source());
        metadata.put("promptName", promptMetadata.promptName());
        metadata.put("promptLabel", promptMetadata.promptLabel());
        metadata.put("promptVersion", promptMetadata.promptVersion());
        metadata.put("streaming", streaming);
        metadata.put("environment", properties.getEnvironment());
        metadata.putAll(sanitizeTraceMetadata(traceMetadata));
        if (usage != null) {
            metadata.put("promptTokens", usage.getPromptTokens());
            metadata.put("completionTokens", usage.getCompletionTokens());
            metadata.put("totalTokens", usage.getTotalTokens());
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            metadata.put("error", sanitizeText(errorMessage, METADATA_TEXT_LIMIT));
        }
        return metadata;
    }

    private Map<String, Object> sanitizeTraceMetadata(Map<String, Object> traceMetadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : traceMetadata.entrySet()) {
            String key = entry.getKey();
            if (key == null || RESERVED_TRACE_METADATA_KEYS.contains(key) || HIGH_RISK_METADATA_KEYS.contains(key)) {
                continue;
            }
            sanitized.put(key, sanitizeMetadataValue(entry.getValue()));
        }
        return sanitized;
    }

    private Object sanitizeMetadataValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return sanitizeText(text, METADATA_TEXT_LIMIT);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                sanitized.put(String.valueOf(entry.getKey()), sanitizeMetadataValue(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::sanitizeMetadataValue)
                    .toList();
        }
        return sanitizeText(String.valueOf(value), METADATA_TEXT_LIMIT);
    }

    private List<String> buildTags(ProviderRuntime runtime,
                                   PromptMetadata promptMetadata,
                                   Map<String, Object> traceMetadata,
                                   boolean streaming) {
        List<String> tags = new ArrayList<>();
        tags.add("provider:" + runtime.providerId());
        tags.add("mode:" + (streaming ? "stream" : "sync"));
        tags.add("prompt-source:" + sanitizeTagValue(promptMetadata.source()));
        String entrypoint = extractTraceString(traceMetadata, "entrypoint");
        if (entrypoint != null && !entrypoint.isBlank()) {
            tags.add("entrypoint:" + sanitizeTagValue(entrypoint));
        }
        Object customTags = traceMetadata.get("tags");
        if (customTags instanceof Collection<?> collection) {
            collection.stream()
                    .map(String::valueOf)
                    .map(this::sanitizeTagValue)
                    .filter(tag -> !tag.isBlank())
                    .forEach(tags::add);
        }
        return tags.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String extractTraceString(Map<String, Object> traceMetadata, String key) {
        Object value = traceMetadata.get(key);
        if (value == null) {
            return null;
        }
        String sanitized = sanitizeText(String.valueOf(value), METADATA_TEXT_LIMIT);
        return sanitized.isBlank() ? null : sanitized;
    }

    private String resolveGenerationName() {
        return properties.getTraceName() + "-generation";
    }

    private String sanitizeTagValue(String value) {
        return sanitizeText(value, 48).replace(' ', '-').replace(':', '-');
    }

    private String sanitizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = EMAIL_PATTERN.matcher(value).replaceAll("[redacted-email]");
        normalized = normalized.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private void recordMetrics(ProviderRuntime runtime, long durationNanos, boolean success, boolean streaming) {
        Timer.builder("app.ai.generation.duration")
                .tag("provider", runtime.providerId())
                .tag("model", runtime.chatModelName())
                .tag("streaming", String.valueOf(streaming))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);

        meterRegistry.counter(
                "app.ai.generation.requests",
                "provider", runtime.providerId(),
                "model", runtime.chatModelName(),
                "streaming", String.valueOf(streaming),
                "success", String.valueOf(success)
        ).increment();
    }

    private record TraceEnvelope(
            Map<String, Object> traceInput,
            Map<String, Object> traceOutput,
            Map<String, Object> generationInput,
            Map<String, Object> generationOutput,
            Map<String, Object> traceMetadata,
            Map<String, Object> generationMetadata,
            String userId,
            String sessionId,
            List<String> tags,
            String model,
            PromptMetadata promptMetadata
    ) {
    }
}
