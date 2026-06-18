package com.github.mail.service.ai;

import com.github.mail.service.ai.langfuse.LangfuseAdvisor;
import com.github.mail.service.ai.langfuse.LangfuseEvaluationService;
import com.github.mail.service.ai.langfuse.LangfuseTracingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.EmbeddingModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiGenerationServiceTest {

    @Test
    void generate_returnsAggregatedResponse() {
        ChatProviderRegistry registry = mock(ChatProviderRegistry.class);
        AiPromptService promptService = mock(AiPromptService.class);
        LangfuseTracingService tracingService = mock(LangfuseTracingService.class);
        LangfuseEvaluationService evaluationService = mock(LangfuseEvaluationService.class);
        LangfuseAdvisor advisor = new LangfuseAdvisor(new SimpleMeterRegistry());
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        ProviderRuntime providerRuntime = new ProviderRuntime(
                "default",
                "https://example.com/v1",
                "chat-model",
                "embed-model",
                chatModel,
                embeddingModel
        );
        when(registry.getProvider(null)).thenReturn(providerRuntime);
        when(tracingService.newTraceId()).thenReturn("trace-1");
        when(promptService.preparePrompt(any())).thenReturn(new PreparedPrompt(
                new org.springframework.ai.chat.prompt.Prompt("hello"),
                new PromptMetadata("fallback", "mail-auto-reply", "production", 1)
        ));

        ChatResponse response = chatResponse("生成结果", 10, 20, 30);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);

        SpringAiGenerationService service = new SpringAiGenerationService(
                registry,
                promptService,
                tracingService,
                evaluationService,
                advisor
        );

        AiGenerationResult result = service.generate(new AiGenerationRequest(null, "query", List.of(), Map.of()));

        assertEquals("生成结果", result.content());
        assertEquals("default", result.providerId());
        assertEquals("trace-1", result.traceId());
        assertEquals(10, result.inputTokens());
        assertEquals(20, result.outputTokens());
        assertEquals(30, result.totalTokens());
        verify(tracingService).recordSuccess(eq("trace-1"), any(), any(), eq(providerRuntime), eq("生成结果"), any(), any(long.class), eq(false));
        verify(evaluationService).recordHeuristicScores("trace-1", new AiGenerationRequest(null, "query", List.of(), Map.of()), "生成结果");
    }

    @Test
    void stream_emitsAllChunksAndRecordsTrace() {
        ChatProviderRegistry registry = mock(ChatProviderRegistry.class);
        AiPromptService promptService = mock(AiPromptService.class);
        LangfuseTracingService tracingService = mock(LangfuseTracingService.class);
        LangfuseEvaluationService evaluationService = mock(LangfuseEvaluationService.class);
        LangfuseAdvisor advisor = new LangfuseAdvisor(new SimpleMeterRegistry());
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        ProviderRuntime providerRuntime = new ProviderRuntime(
                "default",
                "https://example.com/v1",
                "chat-model",
                "embed-model",
                chatModel,
                embeddingModel
        );
        when(registry.getProvider(null)).thenReturn(providerRuntime);
        when(tracingService.newTraceId()).thenReturn("trace-stream");
        when(promptService.preparePrompt(any())).thenReturn(new PreparedPrompt(
                new org.springframework.ai.chat.prompt.Prompt("hello"),
                new PromptMetadata("fallback", "mail-auto-reply", "production", 1)
        ));
        when(chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(
                Flux.just(
                        chatResponse("你", 1, 1, 2),
                        chatResponse("好", 1, 1, 2)
                )
        );

        SpringAiGenerationService service = new SpringAiGenerationService(
                registry,
                promptService,
                tracingService,
                evaluationService,
                advisor
        );

        List<String> chunks = service.stream(new AiGenerationRequest(null, "query", List.of(), Map.of())).collectList().block();

        assertEquals(List.of("你", "好"), chunks);
        verify(tracingService).recordSuccess(eq("trace-stream"), any(), any(), eq(providerRuntime), eq("你好"), any(), any(long.class), eq(true));
        verify(evaluationService).recordHeuristicScores("trace-stream", new AiGenerationRequest(null, "query", List.of(), Map.of()), "你好");
    }

    private ChatResponse chatResponse(String content, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
                        .build()
        );
    }
}
