package com.github.mail.service.ai;

import com.github.mail.config.properties.AppAiProperties;
import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.repo.AiRule.domain.AiReplyRule;
import com.github.mail.repo.AiRule.domain.AiReplyStrategy;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.service.AiRule.AiReplyRuleService;
import com.github.mail.service.AiRule.AiReplyStrategyService;
import com.github.mail.service.ai.langfuse.LangfuseClientFactory;
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

import java.util.Comparator;
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

    private final AiReplyRuleService aiReplyRuleService;
    private final AiReplyStrategyService aiReplyStrategyService;
    private final AppAiProperties appAiProperties;
    private final LangfuseProperties langfuseProperties;
    private final LangfuseClientFactory langfuseClientFactory;

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
            List<Message> messages = prompt.visit(new com.langfuse.client.resources.prompts.types.Prompt.Visitor<List<Message>>() {
                @Override
                public List<Message> visitChat(ChatPrompt prompt) {
                    return toSpringMessages(prompt, variables);
                }

                @Override
                public List<Message> visitText(TextPrompt prompt) {
                    String compiled = compileTemplate(prompt.getPrompt(), variables);
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
        } catch (Exception e) {
            log.warn("Langfuse Prompt 拉取失败，使用本地兜底 Prompt", e);
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
            public PromptMetadata visitChat(ChatPrompt prompt) {
                return new PromptMetadata(
                        SOURCE_LANGFUSE,
                        prompt.getName(),
                        langfuseProperties.getPromptLabel(),
                        prompt.getVersion()
                );
            }

            @Override
            public PromptMetadata visitText(TextPrompt prompt) {
                return new PromptMetadata(
                        SOURCE_LANGFUSE,
                        prompt.getName(),
                        langfuseProperties.getPromptLabel(),
                        prompt.getVersion()
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
        String knowledgeContext = String.valueOf(variables.get("knowledgeContext"));
        String replyRules = String.valueOf(variables.get("replyRules"));
        String strategy = String.valueOf(variables.get("replyStrategy"));
        String userQuery = String.valueOf(variables.get("userQuery"));

        String userTemplate = """
                ## 回复要求
                {{replyRules}}

                ## 回复策略
                {{replyStrategy}}

                ## 知识库相关内容
                {{knowledgeContext}}

                ## 用户邮件内容
                {{userQuery}}

                请根据上述内容生成一封专业、准确、礼貌的回复邮件。
                """;

        String compiledUserPrompt = compileTemplate(userTemplate, Map.of(
                "replyRules", replyRules,
                "replyStrategy", strategy,
                "knowledgeContext", knowledgeContext,
                "userQuery", userQuery
        ));

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
        variables.put("replyRules", buildRuleText());
        variables.put("replyStrategy", buildStrategyText());
        variables.put("knowledgeContext", buildKnowledgeContext(request.ragChunks()));
        variables.put("userQuery", request.userQuery());
        variables.put("ragChunkCount", request.ragChunks().size());
        return variables;
    }

    private String buildRuleText() {
        List<AiReplyRule> rules = aiReplyRuleService.getAllRule().stream()
                .sorted(Comparator.comparingInt(AiReplyRule::getRuleOrder))
                .toList();
        if (rules.isEmpty()) {
            return "1. 回复要专业、礼貌、简洁。\n2. 不要编造未提供的信息。";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rules.size(); i++) {
            builder.append(i + 1)
                    .append(". ")
                    .append(rules.get(i).getRuleText())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String buildStrategyText() {
        AiReplyStrategy strategy = aiReplyStrategyService.getCurrentStrategy();
        if (strategy == null) {
            return "语气: 专业正式\n长度: 适中长度\n是否包含步骤: 不包含步骤";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("语气: ").append(mapToneToChinese(strategy.getTone())).append('\n');
        builder.append("长度: ").append(mapLengthToChinese(strategy.getLength())).append('\n');
        builder.append("是否包含步骤: ")
                .append(strategy.getIncludeSteps() == 1 ? "包含步骤" : "不包含步骤");
        if (strategy.getExtraInstruction() != null && !strategy.getExtraInstruction().isBlank()) {
            builder.append('\n').append("补充说明: ").append(strategy.getExtraInstruction());
        }
        return builder.toString();
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

    private List<Message> toSpringMessages(ChatPrompt prompt, Map<String, Object> variables) {
        return prompt.getPrompt().stream()
                .map(item -> item.visit(new com.langfuse.client.resources.prompts.types.ChatMessageWithPlaceholders.Visitor<Message>() {
                    @Override
                    public Message visit(ChatMessage message) {
                        String compiledContent = compileTemplate(message.getContent(), variables);
                        return switch (message.getRole().toLowerCase()) {
                            case "system" -> new SystemMessage(compiledContent);
                            case "assistant" -> new AssistantMessage(compiledContent);
                            default -> new UserMessage(compiledContent);
                        };
                    }

                    @Override
                    public Message visit(PlaceholderMessage placeholderMessage) {
                        Object value = variables.getOrDefault(placeholderMessage.getName(), "");
                        return new UserMessage(String.valueOf(value));
                    }
                }))
                .toList();
    }

    private String compileTemplate(String template, Map<String, Object> variables) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String mapToneToChinese(String tone) {
        if (tone == null) {
            return "默认";
        }
        return switch (tone.toLowerCase()) {
            case "professional" -> "专业正式";
            case "friendly" -> "友好亲切";
            case "firm" -> "坚定明确";
            default -> tone;
        };
    }

    private String mapLengthToChinese(String length) {
        if (length == null) {
            return "适中长度";
        }
        return switch (length.toLowerCase()) {
            case "short" -> "简短回答";
            case "medium" -> "适中长度";
            case "detailed" -> "详细说明";
            default -> length;
        };
    }
}
