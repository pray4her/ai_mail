package com.github.mail.service.ai;

import com.github.mail.service.File.MinioStorageService;
import com.github.mail.service.ai.langfuse.LangfuseAdvisor;
import com.github.mail.service.ai.langfuse.LangfuseEvaluationService;
import com.github.mail.service.ai.langfuse.LangfuseTracingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiGenerationService implements AiGenerationService {

    private static final Pattern LEADING_HTTP_STATUS = Pattern.compile("^(\\d{3})\\s*-");

    private final ChatProviderRegistry providerRegistry;
    private final AiPromptService promptService;
    private final LangfuseTracingService tracingService;
    private final LangfuseEvaluationService evaluationService;
    private final LangfuseAdvisor langfuseAdvisor;
    private final MinioStorageService minioStorageService;

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        ProviderRuntime runtime = providerRegistry.getProvider(request.providerId());
        String traceId = tracingService.newTraceId();
        long startNanos = System.nanoTime();
        PreparedPrompt preparedPrompt = null;

        try {
            preparedPrompt = promptService.preparePrompt(request);
            GenerationOutcome outcome = supportsOpenRouterNative(runtime, request)
                    ? generateWithOpenRouterNative(runtime, request, preparedPrompt)
                    : generateWithChatClient(runtime, request, preparedPrompt);

            tracingService.recordSuccess(
                    traceId,
                    outcome.request(),
                    outcome.preparedPrompt(),
                    runtime,
                    outcome.content(),
                    outcome.usage(),
                    System.nanoTime() - startNanos,
                    false
            );
            evaluationService.recordHeuristicScores(traceId, outcome.request(), outcome.content());

            return new AiGenerationResult(
                    outcome.content(),
                    runtime.providerId(),
                    runtime.chatModelName(),
                    outcome.preparedPrompt().metadata(),
                    outcome.usage() == null ? null : outcome.usage().getPromptTokens(),
                    outcome.usage() == null ? null : outcome.usage().getCompletionTokens(),
                    outcome.usage() == null ? null : outcome.usage().getTotalTokens(),
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
        String traceId = tracingService.newTraceId();
        long startNanos = System.nanoTime();
        PreparedPrompt preparedPrompt;
        try {
            preparedPrompt = promptService.preparePrompt(request);
        } catch (Exception e) {
            tracingService.recordFailure(
                    traceId,
                    request,
                    null,
                    runtime,
                    e,
                    System.nanoTime() - startNanos,
                    true
            );
            return Flux.error(new IllegalStateException("AI 流式生成失败", e));
        }
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

    private boolean supportsOpenRouterNative(ProviderRuntime runtime, AiGenerationRequest request) {
        return request.useNativeAttachments()
                && request.attachments() != null
                && !request.attachments().isEmpty()
                && runtime.baseUrl() != null
                && runtime.baseUrl().toLowerCase().contains("openrouter.ai");
    }

    private GenerationOutcome generateWithChatClient(ProviderRuntime runtime,
                                                     AiGenerationRequest request,
                                                     PreparedPrompt preparedPrompt) {
        ChatResponse response = ChatClient.create(runtime.chatModel())
                .prompt(preparedPrompt.prompt())
                .advisors(spec -> spec.advisors(langfuseAdvisor))
                .call()
                .chatResponse();
        return new GenerationOutcome(
                extractContent(response),
                response.getMetadata().getUsage(),
                request,
                preparedPrompt
        );
    }

    private GenerationOutcome generateWithOpenRouterNative(ProviderRuntime runtime,
                                                           AiGenerationRequest request,
                                                           PreparedPrompt preparedPrompt) {
        try {
            NativeResponse nativeResponse = executeOpenRouterNative(runtime, preparedPrompt, request.attachments());
            return new GenerationOutcome(nativeResponse.content(), nativeResponse.usage(), request, preparedPrompt);
        } catch (Exception exception) {
            Integer statusCode = extractStatusCode(exception);
            if (!isAttachmentValidationFailure(exception, statusCode)) {
                throw propagate(exception);
            }
            Map<String, Object> fallbackTraceMetadata = new LinkedHashMap<>(request.traceMetadata());
            fallbackTraceMetadata.put("attachmentNativeFallback", true);
            fallbackTraceMetadata.put("attachmentNativeFallbackStatus", statusCode == null ? "" : statusCode);
            AiGenerationRequest fallbackRequest = new AiGenerationRequest(
                    request.providerId(),
                    request.userQuery(),
                    request.ragChunks(),
                    request.attachments(),
                    fallbackTraceMetadata,
                    false
            );
            PreparedPrompt fallbackPrompt = promptService.preparePrompt(fallbackRequest);
            return generateWithChatClient(runtime, fallbackRequest, fallbackPrompt);
        }
    }

    private NativeResponse executeOpenRouterNative(ProviderRuntime runtime,
                                                   PreparedPrompt preparedPrompt,
                                                   List<AiInputAttachment> attachments) {
        List<OpenAiApi.ChatCompletionMessage> messages = buildOpenRouterMessages(
                preparedPrompt.prompt().getInstructions(),
                attachments
        );
        OpenAiApi.ChatCompletionRequest request = buildNativeRequest(runtime, messages, attachments);
        OpenAiApi.ChatCompletion completion = runtime.openAiApi().chatCompletionEntity(request).getBody();
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new IllegalStateException("OpenRouter native 请求未返回结果");
        }
        OpenAiApi.ChatCompletionMessage message = completion.choices().get(0).message();
        String content = message == null || message.content() == null ? "" : message.content();
        return new NativeResponse(content, toUsage(completion.usage()));
    }

    private List<OpenAiApi.ChatCompletionMessage> buildOpenRouterMessages(List<Message> instructions,
                                                                          List<AiInputAttachment> attachments) {
        List<OpenAiApi.ChatCompletionMessage> results = new ArrayList<>();
        boolean attachmentsInjected = false;
        for (Message instruction : instructions) {
            switch (instruction.getMessageType()) {
                case SYSTEM -> results.add(new OpenAiApi.ChatCompletionMessage(
                        instruction.getText(),
                        OpenAiApi.ChatCompletionMessage.Role.SYSTEM
                ));
                case ASSISTANT -> results.add(new OpenAiApi.ChatCompletionMessage(
                        instruction.getText(),
                        OpenAiApi.ChatCompletionMessage.Role.ASSISTANT
                ));
                default -> {
                    Object rawContent = attachmentsInjected
                            ? instruction.getText()
                            : buildUserRawContent(instruction.getText(), attachments);
                    results.add(new OpenAiApi.ChatCompletionMessage(
                            rawContent,
                            OpenAiApi.ChatCompletionMessage.Role.USER
                    ));
                    attachmentsInjected = true;
                }
            }
        }
        if (!attachmentsInjected && attachments != null && !attachments.isEmpty()) {
            results.add(new OpenAiApi.ChatCompletionMessage(
                    buildUserRawContent("", attachments),
                    OpenAiApi.ChatCompletionMessage.Role.USER
            ));
        }
        return results;
    }

    private Object buildUserRawContent(String text, List<AiInputAttachment> attachments) {
        List<OpenAiApi.ChatCompletionMessage.MediaContent> content = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            content.add(new OpenAiApi.ChatCompletionMessage.MediaContent(text));
        }
        for (AiInputAttachment attachment : attachments) {
            String resolvedMimeType = resolveAttachmentMimeType(attachment);
            String dataUrl = toDataUrl(resolvedMimeType, minioStorageService.readBytes(attachment.storagePath()));
            if (attachment.isImage()) {
                content.add(new OpenAiApi.ChatCompletionMessage.MediaContent(
                        new OpenAiApi.ChatCompletionMessage.MediaContent.ImageUrl(dataUrl)
                ));
                continue;
            }
            content.add(new OpenAiApi.ChatCompletionMessage.MediaContent(
                    new OpenAiApi.ChatCompletionMessage.MediaContent.InputFile(
                            attachment.filename(),
                            dataUrl
                    )
            ));
        }
        return content;
    }

    private OpenAiApi.ChatCompletionRequest buildNativeRequest(ProviderRuntime runtime,
                                                               List<OpenAiApi.ChatCompletionMessage> messages,
                                                               List<AiInputAttachment> attachments) {
        OpenAiChatOptions options = OpenAiChatOptions.fromOptions(runtime.defaultChatOptions());
        Map<String, Object> extraBody = mergeExtraBody(options.getExtraBody(), buildPdfPluginBody(attachments));
        return new OpenAiApi.ChatCompletionRequest(
                messages,
                options.getModel(),
                options.getStore(),
                options.getMetadata(),
                options.getFrequencyPenalty(),
                options.getLogitBias(),
                options.getLogprobs(),
                options.getTopLogprobs(),
                options.getMaxTokens(),
                options.getMaxCompletionTokens(),
                options.getN(),
                null,
                options.getOutputAudio(),
                options.getPresencePenalty(),
                options.getResponseFormat(),
                options.getSeed(),
                options.getServiceTier(),
                options.getStop(),
                false,
                null,
                options.getTemperature(),
                options.getTopP(),
                options.getTools(),
                options.getToolChoice(),
                options.getParallelToolCalls(),
                options.getUser(),
                options.getReasoningEffort(),
                options.getWebSearchOptions(),
                options.getVerbosity(),
                options.getPromptCacheKey(),
                options.getSafetyIdentifier(),
                extraBody
        );
    }

    private Map<String, Object> buildPdfPluginBody(List<AiInputAttachment> attachments) {
        boolean hasPdf = attachments.stream().anyMatch(AiInputAttachment::isPdf);
        if (!hasPdf) {
            return Map.of();
        }
        return Map.of(
                "plugins",
                List.of(Map.of("id", "file-parser"))
        );
    }

    private Map<String, Object> mergeExtraBody(Map<String, Object> base, Map<String, Object> extra) {
        if ((base == null || base.isEmpty()) && (extra == null || extra.isEmpty())) {
            return null;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (extra != null) {
            merged.putAll(extra);
        }
        return merged;
    }

    private Usage toUsage(OpenAiApi.Usage usage) {
        if (usage == null) {
            return null;
        }
        return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    }

    private boolean isAttachmentValidationFailure(Throwable throwable, Integer statusCode) {
        if (statusCode != null && statusCode != 400 && statusCode != 422) {
            return false;
        }
        String message = buildThrowableMessage(throwable).toLowerCase(Locale.ROOT);
        return message.contains("invalid file data")
                || message.contains("unsupported mime type")
                || message.contains("file_data")
                || throwable instanceof IllegalArgumentException;
    }

    private Integer extractStatusCode(Throwable throwable) {
        RestClientResponseException responseException = findCause(throwable, RestClientResponseException.class);
        if (responseException != null) {
            return responseException.getRawStatusCode();
        }
        String message = throwable.getMessage();
        if (message == null) {
            return null;
        }
        Matcher matcher = LEADING_HTTP_STATUS.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private RuntimeException propagate(Exception exception) {
        return exception instanceof RuntimeException runtimeException
                ? runtimeException
                : new IllegalStateException(exception);
    }

    private String buildThrowableMessage(Throwable throwable) {
        StringBuilder messageBuilder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (!messageBuilder.isEmpty()) {
                    messageBuilder.append(' ');
                }
                messageBuilder.append(current.getMessage());
            }
            current = current.getCause();
        }
        return messageBuilder.toString();
    }

    private String resolveAttachmentMimeType(AiInputAttachment attachment) {
        String normalizedMimeType = normalizeMimeType(attachment.mimeType());
        if (normalizedMimeType != null && !isGenericBinaryMimeType(normalizedMimeType)) {
            return normalizedMimeType;
        }
        String inferredFromFilename = inferMimeTypeFromFilename(attachment.filename());
        if (inferredFromFilename != null) {
            return inferredFromFilename;
        }
        throw new IllegalArgumentException("Unsupported native attachment MIME type: " + attachment.filename());
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        int separatorIndex = mimeType.indexOf(';');
        String normalized = (separatorIndex >= 0 ? mimeType.substring(0, separatorIndex) : mimeType)
                .trim()
                .toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isGenericBinaryMimeType(String mimeType) {
        return "application/octet-stream".equalsIgnoreCase(mimeType);
    }

    private String inferMimeTypeFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        if (normalizedFilename.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (normalizedFilename.endsWith(".doc")) {
            return "application/msword";
        }
        if (normalizedFilename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (normalizedFilename.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (normalizedFilename.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (normalizedFilename.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        }
        if (normalizedFilename.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (normalizedFilename.endsWith(".rtf")) {
            return "application/rtf";
        }
        if (normalizedFilename.endsWith(".txt")) {
            return "text/plain";
        }
        if (normalizedFilename.endsWith(".csv")) {
            return "text/csv";
        }
        if (normalizedFilename.endsWith(".png")) {
            return "image/png";
        }
        if (normalizedFilename.endsWith(".jpg") || normalizedFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalizedFilename.endsWith(".gif")) {
            return "image/gif";
        }
        if (normalizedFilename.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (normalizedFilename.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    private String toDataUrl(String mimeType, byte[] bytes) {
        String resolvedMimeType = mimeType == null || mimeType.isBlank()
                ? "application/octet-stream"
                : normalizeMimeType(mimeType);
        return "data:" + resolvedMimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private record NativeResponse(String content, Usage usage) {
    }

    private record GenerationOutcome(
            String content,
            Usage usage,
            AiGenerationRequest request,
            PreparedPrompt preparedPrompt
    ) {
    }
}
