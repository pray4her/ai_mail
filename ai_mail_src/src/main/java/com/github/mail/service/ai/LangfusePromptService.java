package com.github.mail.service.ai;

import com.github.mail.config.properties.AppAiProperties;
import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.ai.langfuse.LangfuseClientFactory;
import com.github.mail.service.ai.langfuse.LangfusePromptTemplateValidator;
import com.langfuse.client.resources.prompts.requests.GetPromptRequest;
import com.langfuse.client.resources.prompts.types.ChatMessage;
import com.langfuse.client.resources.prompts.types.ChatPrompt;
import com.langfuse.client.resources.prompts.types.PlaceholderMessage;
import com.langfuse.client.resources.prompts.types.TextPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangfusePromptService implements AiPromptService {

    private static final String SOURCE_FALLBACK = "fallback";
    private static final String SOURCE_LANGFUSE = "langfuse";
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final AppAiProperties appAiProperties;
    private final LangfuseProperties langfuseProperties;
    private final LangfuseClientFactory langfuseClientFactory;
    private final LangfusePromptTemplateValidator promptTemplateValidator;

    @Override
    public PreparedPrompt preparePrompt(AiGenerationRequest request) {
        Map<String, Object> variables = buildVariables(request);
        return buildLangfusePrompt(variables).orElseGet(() -> buildFallbackPrompt(variables));
    }

    private Optional<PreparedPrompt> buildLangfusePrompt(Map<String, Object> variables) {
        Optional<com.langfuse.client.LangfuseClient> clientOptional = langfuseClientFactory.getClient();
        if (clientOptional.isEmpty()) {
            return Optional.empty();
        }

        try {
            com.langfuse.client.resources.prompts.types.Prompt prompt = resolvePrompt(clientOptional.get());
            PromptMetadata metadata = resolvePromptMetadata(prompt);
            promptTemplateValidator.validateLangfusePrompt(metadata.promptName(), prompt);
            List<Message> messages = prompt.visit(new com.langfuse.client.resources.prompts.types.Prompt.Visitor<List<Message>>() {
                @Override
                public List<Message> visitChat(ChatPrompt chatPrompt) {
                    return toSpringMessages(chatPrompt, variables);
                }

                @Override
                public List<Message> visitText(TextPrompt textPrompt) {
                    String compiled = compileTemplate(textPrompt.getPrompt(), variables);
                    return List.of(
                            new SystemMessage(appAiProperties.getFallbackSystemPrompt()),
                            new UserMessage(compiled)
                    );
                }

                @Override
                public List<Message> _visitUnknown(Object value) {
                    return List.of(
                            new SystemMessage(appAiProperties.getFallbackSystemPrompt()),
                            new UserMessage(String.valueOf(value))
                    );
                }
            });
            return Optional.of(new PreparedPrompt(new Prompt(messages), metadata));
        } catch (Exception exception) {
            log.warn("Langfuse Prompt 拉取或校验失败，使用本地兜底 Prompt", exception);
            return Optional.empty();
        }
    }

    private com.langfuse.client.resources.prompts.types.Prompt resolvePrompt(com.langfuse.client.LangfuseClient client) {
        if (langfuseProperties.getPromptVersion() > 0) {
            return client.prompts().get(
                    langfuseProperties.getPromptName(),
                    GetPromptRequest.builder().version(langfuseProperties.getPromptVersion()).build()
            );
        }
        if (langfuseProperties.getPromptLabel() != null && !langfuseProperties.getPromptLabel().isBlank()) {
            return client.prompts().get(
                    langfuseProperties.getPromptName(),
                    GetPromptRequest.builder().label(langfuseProperties.getPromptLabel()).build()
            );
        }
        return client.prompts().get(langfuseProperties.getPromptName());
    }

    private PromptMetadata resolvePromptMetadata(com.langfuse.client.resources.prompts.types.Prompt prompt) {
        return prompt.visit(new com.langfuse.client.resources.prompts.types.Prompt.Visitor<PromptMetadata>() {
            @Override
            public PromptMetadata visitChat(ChatPrompt chatPrompt) {
                return new PromptMetadata(
                        SOURCE_LANGFUSE,
                        chatPrompt.getName(),
                        langfuseProperties.getPromptLabel(),
                        chatPrompt.getVersion()
                );
            }

            @Override
            public PromptMetadata visitText(TextPrompt textPrompt) {
                return new PromptMetadata(
                        SOURCE_LANGFUSE,
                        textPrompt.getName(),
                        langfuseProperties.getPromptLabel(),
                        textPrompt.getVersion()
                );
            }

            @Override
            public PromptMetadata _visitUnknown(Object value) {
                return new PromptMetadata(
                        SOURCE_LANGFUSE,
                        langfuseProperties.getPromptName(),
                        langfuseProperties.getPromptLabel(),
                        langfuseProperties.getPromptVersion() > 0 ? langfuseProperties.getPromptVersion() : null
                );
            }
        });
    }

    private PreparedPrompt buildFallbackPrompt(Map<String, Object> variables) {
        String userTemplate = """
                ## 知识库相关内容
                {{knowledgeContext}}

                ## 用户邮件内容
                {{userQuery}}

                ## 历史往来上下文
                {{historyContext}}

                ## 附件概览
                {{attachmentSummary}}

                ## 附件处理方式
                {{nativeAttachmentHint}}

                ## 附件提取文本
                {{fallbackAttachmentText}}

                请结合知识库内容、用户邮件和附件信息，生成一封专业、准确、礼貌的回复邮件。
                只输出邮件正文，不要输出 Subject、主题、收件人、发件人等邮件头字段。
                """;

        String compiledUserPrompt = compileTemplate(userTemplate, variables);
        return new PreparedPrompt(
                new Prompt(List.of(
                        new SystemMessage(appAiProperties.getFallbackSystemPrompt()),
                        new UserMessage(compiledUserPrompt)
                )),
                new PromptMetadata(
                        SOURCE_FALLBACK,
                        langfuseProperties.getPromptName(),
                        langfuseProperties.getPromptLabel(),
                        null
                )
        );
    }

    private Map<String, Object> buildVariables(AiGenerationRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("knowledgeContext", buildKnowledgeContext(request.ragChunks()));
        variables.put("userQuery", request.userQuery());
        variables.put("historyContext", request.historyContext().isBlank()
                ? "（无可用历史往来上下文）"
                : request.historyContext());
        variables.put("ragChunkCount", request.ragChunks().size());
        variables.put("attachmentSummary", buildAttachmentSummary(request.attachments()));
        variables.put("nativeAttachmentHint", request.useNativeAttachments()
                ? "原始附件已随请求一并发送，请结合附件内容分析并生成回复。"
                : "当前未发送原始附件；如果有附件提取文本，请结合下方提取文本理解附件内容。");
        variables.put("fallbackAttachmentText", buildFallbackAttachmentText(request.attachments(), request.useNativeAttachments()));
        return variables;
    }

    private String buildKnowledgeContext(List<RagChunk> ragChunks) {
        if (ragChunks == null || ragChunks.isEmpty()) {
            return "（未找到相关知识库内容）";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ragChunks.size(); i++) {
            RagChunk chunk = ragChunks.get(i);
            builder.append("[片段 ")
                    .append(i + 1)
                    .append("] 相似度: ")
                    .append(String.format("%.3f", chunk.getScore()))
                    .append('\n')
                    .append(chunk.getChunkText())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private String buildAttachmentSummary(List<AiInputAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "（无附件）";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < attachments.size(); i++) {
            AiInputAttachment attachment = attachments.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(attachment.filename())
                    .append(" [")
                    .append(attachment.mimeType())
                    .append("]")
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String buildFallbackAttachmentText(List<AiInputAttachment> attachments, boolean useNativeAttachments) {
        if (useNativeAttachments || attachments == null || attachments.isEmpty()) {
            return "（当前无需附加文本回退）";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (AiInputAttachment attachment : attachments) {
            if (!attachment.hasFallbackExtractedText()) {
                continue;
            }
            builder.append("[附件 ")
                    .append(index++)
                    .append("] ")
                    .append(attachment.filename())
                    .append('\n')
                    .append(attachment.fallbackExtractedText())
                    .append("\n\n");
        }
        return builder.isEmpty() ? "（附件无可提取文本）" : builder.toString().trim();
    }

    private List<Message> toSpringMessages(ChatPrompt prompt, Map<String, Object> variables) {
        List<Message> messages = new ArrayList<>();
        prompt.getPrompt().forEach(item -> item.visit(new com.langfuse.client.resources.prompts.types.ChatMessageWithPlaceholders.Visitor<Void>() {
            @Override
            public Void visit(ChatMessage message) {
                messages.add(toSpringMessage(message.getRole(), compileTemplate(message.getContent(), variables)));
                return null;
            }

            @Override
            public Void visit(PlaceholderMessage placeholderMessage) {
                messages.addAll(resolvePlaceholderMessages(placeholderMessage, variables));
                return null;
            }
        }));
        return messages;
    }

    private List<Message> resolvePlaceholderMessages(PlaceholderMessage placeholderMessage, Map<String, Object> variables) {
        Object value = variables.getOrDefault(placeholderMessage.getName(), "");
        if (value instanceof List<?> listValue) {
            List<Message> resolved = new ArrayList<>();
            for (Object item : listValue) {
                if (item instanceof Map<?, ?> mapValue) {
                    Object role = mapValue.get("role");
                    Object content = mapValue.get("content");
                    if (content != null) {
                        resolved.add(toSpringMessage(
                                role == null ? "user" : String.valueOf(role),
                                String.valueOf(content)
                        ));
                    }
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        return List.of(new UserMessage(String.valueOf(value)));
    }

    private Message toSpringMessage(String role, String content) {
        String normalizedRole = role == null ? "user" : role.toLowerCase();
        return switch (normalizedRole) {
            case "system" -> new SystemMessage(content);
            case "assistant" -> new AssistantMessage(content);
            default -> new UserMessage(content);
        };
    }

    private String compileTemplate(String template, Map<String, Object> variables) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template == null ? "" : template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
