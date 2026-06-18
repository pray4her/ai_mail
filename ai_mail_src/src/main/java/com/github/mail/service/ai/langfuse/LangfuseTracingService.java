package com.github.mail.service.ai.langfuse;

import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.service.ai.AiGenerationRequest;
import com.github.mail.service.ai.PreparedPrompt;
import com.github.mail.service.ai.ProviderRuntime;
import com.langfuse.client.resources.ingestion.requests.IngestionRequest;
import com.langfuse.client.resources.ingestion.types.CreateGenerationBody;
import com.langfuse.client.resources.ingestion.types.CreateGenerationEvent;
import com.langfuse.client.resources.ingestion.types.IngestionEvent;
import com.langfuse.client.resources.ingestion.types.IngestionResponse;
import com.langfuse.client.resources.ingestion.types.IngestionUsage;
import com.langfuse.client.resources.ingestion.types.OpenAiUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangfuseTracingService {

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
            CreateGenerationEvent event = CreateGenerationEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .timestamp(OffsetDateTime.now().toString())
                    .body(CreateGenerationBody.builder()
                            .id(traceId + "-generation")
                            .traceId(traceId)
                            .name(properties.getTraceName())
                            .startTime(OffsetDateTime.now().minusNanos(durationNanos))
                            .endTime(OffsetDateTime.now())
                            .input(Map.of(
                                    "userQuery", request.userQuery(),
                                    "ragChunkCount", request.ragChunks().size()
                            ))
                            .output(Map.of("content", output))
                            .model(runtime.chatModelName())
                            .environment(properties.getEnvironment())
                            .promptName(preparedPrompt.metadata().promptName())
                            .promptVersion(preparedPrompt.metadata().promptVersion())
                            .usage(toUsage(usage))
                            .metadata(buildMetadata(preparedPrompt, runtime, request.traceMetadata(), usage, streaming, null))
                            .build())
                    .build();
            submit(client, event);
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
            CreateGenerationEvent event = CreateGenerationEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .timestamp(OffsetDateTime.now().toString())
                    .body(CreateGenerationBody.builder()
                            .id(traceId + "-generation")
                            .traceId(traceId)
                            .name(properties.getTraceName())
                            .startTime(OffsetDateTime.now().minusNanos(durationNanos))
                            .endTime(OffsetDateTime.now())
                            .input(Map.of(
                                    "userQuery", request.userQuery(),
                                    "ragChunkCount", request.ragChunks().size()
                            ))
                            .output(Map.of())
                            .model(runtime.chatModelName())
                            .environment(properties.getEnvironment())
                            .promptName(preparedPrompt.metadata().promptName())
                            .promptVersion(preparedPrompt.metadata().promptVersion())
                            .statusMessage(error.getMessage())
                            .metadata(buildMetadata(preparedPrompt, runtime, request.traceMetadata(), null, streaming, error.getMessage()))
                            .build())
                    .build();
            submit(client, event);
        });
    }

    private void submit(com.langfuse.client.LangfuseClient client, CreateGenerationEvent event) {
        IngestionRequest request = IngestionRequest.builder()
                .batch(List.of(IngestionEvent.generationCreate(event)))
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

    private Map<String, Object> buildMetadata(PreparedPrompt preparedPrompt,
                                              ProviderRuntime runtime,
                                              Map<String, Object> traceMetadata,
                                              Usage usage,
                                              boolean streaming,
                                              String errorMessage) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("providerId", runtime.providerId());
        metadata.put("baseUrl", runtime.baseUrl());
        metadata.put("chatModel", runtime.chatModelName());
        metadata.put("embeddingModel", runtime.embeddingModelName());
        metadata.put("promptSource", preparedPrompt.metadata().source());
        metadata.put("promptName", preparedPrompt.metadata().promptName());
        metadata.put("promptLabel", preparedPrompt.metadata().promptLabel());
        metadata.put("promptVersion", preparedPrompt.metadata().promptVersion());
        metadata.put("streaming", streaming);
        metadata.put("environment", properties.getEnvironment());
        metadata.putAll(traceMetadata);
        if (usage != null) {
            metadata.put("promptTokens", usage.getPromptTokens());
            metadata.put("completionTokens", usage.getCompletionTokens());
            metadata.put("totalTokens", usage.getTotalTokens());
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            metadata.put("error", errorMessage);
        }
        return metadata;
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
}
