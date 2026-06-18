package com.github.mail.service.ai;

import com.github.mail.service.File.MinioStorageService;
import com.github.mail.service.ai.langfuse.LangfuseAdvisor;
import com.github.mail.service.ai.langfuse.LangfuseEvaluationService;
import com.github.mail.service.ai.langfuse.LangfuseTracingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        OpenAiApi openAiApi = mock(OpenAiApi.class);

        ProviderRuntime providerRuntime = new ProviderRuntime(
                "default",
                "https://example.com/v1",
                "chat-model",
                "embed-model",
                openAiApi,
                OpenAiChatOptions.builder().model("chat-model").build(),
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
                advisor,
                mock(MinioStorageService.class)
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
        OpenAiApi openAiApi = mock(OpenAiApi.class);

        ProviderRuntime providerRuntime = new ProviderRuntime(
                "default",
                "https://example.com/v1",
                "chat-model",
                "embed-model",
                openAiApi,
                OpenAiChatOptions.builder().model("chat-model").build(),
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
                advisor,
                mock(MinioStorageService.class)
        );

        List<String> chunks = service.stream(new AiGenerationRequest(null, "query", List.of(), Map.of())).collectList().block();

        assertEquals(List.of("你", "好"), chunks);
        verify(tracingService).recordSuccess(eq("trace-stream"), any(), any(), eq(providerRuntime), eq("你好"), any(), any(long.class), eq(true));
        verify(evaluationService).recordHeuristicScores("trace-stream", new AiGenerationRequest(null, "query", List.of(), Map.of()), "你好");
    }

    @Test
    void generate_usesOpenRouterNativeRequestForAttachments() {
        ChatProviderRegistry registry = mock(ChatProviderRegistry.class);
        AiPromptService promptService = mock(AiPromptService.class);
        LangfuseTracingService tracingService = mock(LangfuseTracingService.class);
        LangfuseEvaluationService evaluationService = mock(LangfuseEvaluationService.class);
        LangfuseAdvisor advisor = new LangfuseAdvisor(new SimpleMeterRegistry());
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        OpenAiApi openAiApi = mock(OpenAiApi.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);

        ProviderRuntime providerRuntime = new ProviderRuntime(
                "default",
                "https://openrouter.ai/api/v1",
                "google/gemma-3-27b-it",
                "embed-model",
                openAiApi,
                OpenAiChatOptions.builder().model("google/gemma-3-27b-it").build(),
                chatModel,
                embeddingModel
        );
        when(registry.getProvider(null)).thenReturn(providerRuntime);
        when(tracingService.newTraceId()).thenReturn("trace-native");
        when(promptService.preparePrompt(any())).thenReturn(new PreparedPrompt(
                new org.springframework.ai.chat.prompt.Prompt("hello"),
                new PromptMetadata("fallback", "mail-auto-reply", "production", 1)
        ));
        when(minioStorageService.readBytes("mail-attachments/11/file.pdf")).thenReturn("pdf".getBytes());
        when(minioStorageService.readBytes("mail-attachments/11/image.png")).thenReturn("img".getBytes());
        when(openAiApi.chatCompletionEntity(any(OpenAiApi.ChatCompletionRequest.class)))
                .thenReturn(ResponseEntity.ok(nativeCompletion("原生生成结果")));

        SpringAiGenerationService service = new SpringAiGenerationService(
                registry,
                promptService,
                tracingService,
                evaluationService,
                advisor,
                minioStorageService
        );

        AiGenerationResult result = service.generate(new AiGenerationRequest(
                null,
                "query",
                List.of(),
                List.of(
                        new AiInputAttachment(1L, "file.pdf", "application/pdf", "mail-attachments/11/file.pdf", "hash-pdf", "pdf text"),
                        new AiInputAttachment(2L, "image.png", "image/png", "mail-attachments/11/image.png", "hash-img", null)
                ),
                Map.of(),
                true
        ));

        assertEquals("原生生成结果", result.content());
        verify(openAiApi).chatCompletionEntity(any(OpenAiApi.ChatCompletionRequest.class));
    }

    @Test
    void generate_infersSpecificMimeTypeBeforeSendingNativeAttachment() {
        ChatProviderRegistry registry = mock(ChatProviderRegistry.class);
        AiPromptService promptService = mock(AiPromptService.class);
        LangfuseTracingService tracingService = mock(LangfuseTracingService.class);
        LangfuseEvaluationService evaluationService = mock(LangfuseEvaluationService.class);
        LangfuseAdvisor advisor = new LangfuseAdvisor(new SimpleMeterRegistry());
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        OpenAiApi openAiApi = mock(OpenAiApi.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);

        ProviderRuntime providerRuntime = new ProviderRuntime(
                "default",
                "https://openrouter.ai/api/v1",
                "google/gemma-3-27b-it",
                "embed-model",
                openAiApi,
                OpenAiChatOptions.builder().model("google/gemma-3-27b-it").build(),
                chatModel,
                embeddingModel
        );
        when(registry.getProvider(null)).thenReturn(providerRuntime);
        when(tracingService.newTraceId()).thenReturn("trace-native-docx");
        when(promptService.preparePrompt(any())).thenReturn(new PreparedPrompt(
                new org.springframework.ai.chat.prompt.Prompt("hello"),
                new PromptMetadata("fallback", "mail-auto-reply", "production", 1)
        ));
        when(minioStorageService.readBytes("mail-attachments/11/file.docx")).thenReturn("docx".getBytes());
        when(openAiApi.chatCompletionEntity(any(OpenAiApi.ChatCompletionRequest.class)))
                .thenReturn(ResponseEntity.ok(nativeCompletion("docx native")));

        SpringAiGenerationService service = new SpringAiGenerationService(
                registry,
                promptService,
                tracingService,
                evaluationService,
                advisor,
                minioStorageService
        );

        AiGenerationResult result = service.generate(new AiGenerationRequest(
                null,
                "query",
                List.of(),
                List.of(new AiInputAttachment(1L, "file.docx", "APPLICATION/OCTET-STREAM", "mail-attachments/11/file.docx", "hash-docx", "docx text")),
                Map.of(),
                true
        ));

        ArgumentCaptor<OpenAiApi.ChatCompletionRequest> requestCaptor = ArgumentCaptor.forClass(OpenAiApi.ChatCompletionRequest.class);
        verify(openAiApi).chatCompletionEntity(requestCaptor.capture());
        Object rawContent = requestCaptor.getValue().messages().get(0).rawContent();
        assertTrue(rawContent instanceof List<?>);
        List<?> mediaContents = (List<?>) rawContent;
        Object filePayload = mediaContents.get(1);
        assertNotNull(filePayload);
        assertTrue(filePayload.toString().contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("docx native", result.content());
    }

    @Test
    void generate_fallsBackToTextWhenSpringAiWrapsAttachmentValidationError() {
        ChatProviderRegistry registry = mock(ChatProviderRegistry.class);
        AiPromptService promptService = mock(AiPromptService.class);
        LangfuseTracingService tracingService = mock(LangfuseTracingService.class);
        LangfuseEvaluationService evaluationService = mock(LangfuseEvaluationService.class);
        LangfuseAdvisor advisor = new LangfuseAdvisor(new SimpleMeterRegistry());
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        OpenAiApi openAiApi = mock(OpenAiApi.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);

        ProviderRuntime providerRuntime = new ProviderRuntime(
                "default",
                "https://openrouter.ai/api/v1",
                "google/gemma-3-27b-it",
                "embed-model",
                openAiApi,
                OpenAiChatOptions.builder().model("google/gemma-3-27b-it").build(),
                chatModel,
                embeddingModel
        );
        when(registry.getProvider(null)).thenReturn(providerRuntime);
        when(tracingService.newTraceId()).thenReturn("trace-fallback");
        when(promptService.preparePrompt(any())).thenReturn(new PreparedPrompt(
                new org.springframework.ai.chat.prompt.Prompt("hello"),
                new PromptMetadata("fallback", "mail-auto-reply", "production", 1)
        ));
        when(minioStorageService.readBytes("mail-attachments/11/file.docx")).thenReturn("docx".getBytes());
        when(openAiApi.chatCompletionEntity(any(OpenAiApi.ChatCompletionRequest.class)))
                .thenThrow(new NonTransientAiException("400 - Invalid file data: 'input[1].content[1].file_data'"));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponse("回退结果", 10, 20, 30));

        SpringAiGenerationService service = new SpringAiGenerationService(
                registry,
                promptService,
                tracingService,
                evaluationService,
                advisor,
                minioStorageService
        );

        AiGenerationResult result = service.generate(new AiGenerationRequest(
                null,
                "query",
                List.of(),
                List.of(new AiInputAttachment(1L, "file.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "mail-attachments/11/file.docx", "hash-docx", "docx text")),
                Map.of(),
                true
        ));

        assertEquals("回退结果", result.content());
        verify(openAiApi, times(1)).chatCompletionEntity(any(OpenAiApi.ChatCompletionRequest.class));
        verify(chatModel, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    private ChatResponse chatResponse(String content, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(promptTokens, completionTokens, totalTokens))
                        .build()
        );
    }

    private OpenAiApi.ChatCompletion nativeCompletion(String content) {
        return new OpenAiApi.ChatCompletion(
                "id-1",
                List.of(new OpenAiApi.ChatCompletion.Choice(
                        OpenAiApi.ChatCompletionFinishReason.STOP,
                        0,
                        new OpenAiApi.ChatCompletionMessage(content, OpenAiApi.ChatCompletionMessage.Role.ASSISTANT),
                        null
                )),
                1L,
                "model",
                null,
                null,
                "chat.completion",
                new OpenAiApi.Usage(10, 20, 30, null, null)
        );
    }
}
