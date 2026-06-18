package com.github.mail.service.ai;

import com.github.mail.service.ai.langfuse.LangfuseAdvisor;
import com.github.mail.service.ai.langfuse.LangfuseEvaluationService;
import com.github.mail.service.ai.langfuse.LangfuseTracingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiGenerationService implements AiGenerationService {

    private final ChatProviderRegistry providerRegistry;
    private final AiPromptService promptService;
    private final LangfuseTracingService tracingService;
    private final LangfuseEvaluationService evaluationService;
    private final LangfuseAdvisor langfuseAdvisor;

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        ProviderRuntime runtime = providerRegistry.getProvider(request.providerId());
        PreparedPrompt preparedPrompt = promptService.preparePrompt(request);
        String traceId = tracingService.newTraceId();
        long startNanos = System.nanoTime();

        try {
            ChatResponse response = ChatClient.create(runtime.chatModel())
                    .prompt(preparedPrompt.prompt())
                    .advisors(spec -> spec.advisors(langfuseAdvisor))
                    .call()
                    .chatResponse();

            String content = extractContent(response);
            Usage usage = response.getMetadata().getUsage();
            tracingService.recordSuccess(
                    traceId,
                    request,
                    preparedPrompt,
                    runtime,
                    content,
                    usage,
                    System.nanoTime() - startNanos,
                    false
            );
            evaluationService.recordHeuristicScores(traceId, request, content);

            return new AiGenerationResult(
                    content,
                    runtime.providerId(),
                    runtime.chatModelName(),
                    preparedPrompt.metadata(),
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    usage == null ? null : usage.getTotalTokens(),
                    traceId
            );
        } catch (Exception e) {
            tracingService.recordFailure(
                    traceId,
                    request,
                    preparedPrompt,
                    runtime,
                    e,
                    System.nanoTime() - startNanos,
                    false
            );
            throw new IllegalStateException("AI 同步生成失败", e);
        }
    }

    @Override
    public Flux<String> stream(AiGenerationRequest request) {
        ProviderRuntime runtime = providerRegistry.getProvider(request.providerId());
        PreparedPrompt preparedPrompt = promptService.preparePrompt(request);
        String traceId = tracingService.newTraceId();
        long startNanos = System.nanoTime();
        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<Usage> usageRef = new AtomicReference<>();

        return ChatClient.create(runtime.chatModel())
                .prompt(preparedPrompt.prompt())
                .advisors(spec -> spec.advisors(langfuseAdvisor))
                .stream()
                .chatResponse()
                .map(response -> {
                    usageRef.set(response.getMetadata().getUsage());
                    String chunk = extractContent(response);
                    if (chunk != null && !chunk.isEmpty()) {
                        contentBuilder.append(chunk);
                    }
                    return chunk;
                })
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnComplete(() -> {
                    tracingService.recordSuccess(
                            traceId,
                            request,
                            preparedPrompt,
                            runtime,
                            contentBuilder.toString(),
                            usageRef.get(),
                            System.nanoTime() - startNanos,
                            true
                    );
                    evaluationService.recordHeuristicScores(traceId, request, contentBuilder.toString());
                })
                .doOnError(error -> tracingService.recordFailure(
                        traceId,
                        request,
                        preparedPrompt,
                        runtime,
                        error,
                        System.nanoTime() - startNanos,
                        true
                ));
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String content = response.getResult().getOutput().getText();
        return content == null ? "" : content;
    }
}
