package com.github.mail.service.ai.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import com.langfuse.client.resources.prompts.types.ChatMessage;
import com.langfuse.client.resources.prompts.types.ChatPrompt;
import com.langfuse.client.resources.prompts.types.PlaceholderMessage;
import com.langfuse.client.resources.prompts.types.TextPrompt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LangfusePromptTemplateValidator {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final List<String> RESERVED_VARIABLES = List.of("userQuery", "knowledgeContext");
    private static final List<String> OPTIONAL_VARIABLES = List.of(
            "attachmentSummary",
            "nativeAttachmentHint",
            "fallbackAttachmentText",
            "ragChunkCount"
    );

    public List<String> getReservedVariables() {
        return RESERVED_VARIABLES;
    }

    public List<String> getOptionalVariables() {
        return OPTIONAL_VARIABLES;
    }

    public void validateRequestPrompt(String promptName, String promptType, JsonNode promptNode) {
        if (promptType == null || promptType.isBlank()) {
            throw new IllegalArgumentException("Prompt type 不能为空");
        }
        if (promptNode == null || promptNode.isNull()) {
            throw new IllegalArgumentException("Prompt 内容不能为空");
        }

        Set<String> variables = switch (promptType.toLowerCase()) {
            case "text" -> extractTextPromptVariables(promptNode);
            case "chat" -> extractChatPromptVariables(promptNode);
            default -> throw new IllegalArgumentException("不支持的 Prompt 类型: " + promptType);
        };
        ensureReservedVariables(promptName, variables);
    }

    public void validateLangfusePrompt(String promptName, com.langfuse.client.resources.prompts.types.Prompt prompt) {
        Set<String> variables = prompt.visit(new com.langfuse.client.resources.prompts.types.Prompt.Visitor<Set<String>>() {
            @Override
            public Set<String> visitChat(ChatPrompt chatPrompt) {
                Set<String> names = new LinkedHashSet<>();
                chatPrompt.getPrompt().forEach(item -> item.visit(new com.langfuse.client.resources.prompts.types.ChatMessageWithPlaceholders.Visitor<Void>() {
                    @Override
                    public Void visit(ChatMessage message) {
                        names.addAll(extractTemplateVariables(message.getContent()));
                        return null;
                    }

                    @Override
                    public Void visit(PlaceholderMessage placeholderMessage) {
                        if (placeholderMessage.getName() != null && !placeholderMessage.getName().isBlank()) {
                            names.add(placeholderMessage.getName());
                        }
                        return null;
                    }
                }));
                return names;
            }

            @Override
            public Set<String> visitText(TextPrompt textPrompt) {
                return extractTemplateVariables(textPrompt.getPrompt());
            }

            @Override
            public Set<String> _visitUnknown(Object value) {
                return Set.of();
            }
        });
        ensureReservedVariables(promptName, variables);
    }

    private Set<String> extractTextPromptVariables(JsonNode promptNode) {
        if (!promptNode.isTextual()) {
            throw new IllegalArgumentException("text 类型 Prompt 的内容必须是字符串");
        }
        return extractTemplateVariables(promptNode.asText());
    }

    private Set<String> extractChatPromptVariables(JsonNode promptNode) {
        if (!promptNode.isArray()) {
            throw new IllegalArgumentException("chat 类型 Prompt 的内容必须是消息数组");
        }

        Set<String> names = new LinkedHashSet<>();
        for (JsonNode item : promptNode) {
            JsonNode contentNode = item.get("content");
            if (contentNode != null && contentNode.isTextual()) {
                names.addAll(extractTemplateVariables(contentNode.asText()));
            }
            JsonNode typeNode = item.get("type");
            JsonNode nameNode = item.get("name");
            if (nameNode != null
                    && nameNode.isTextual()
                    && (typeNode == null || typeNode.isNull() || "placeholder".equalsIgnoreCase(typeNode.asText()))) {
                names.add(nameNode.asText());
            }
        }
        return names;
    }

    private void ensureReservedVariables(String promptName, Set<String> variables) {
        List<String> missing = RESERVED_VARIABLES.stream()
                .filter(required -> !variables.contains(required))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Prompt " + safePromptName(promptName)
                    + " 缺少保留变量: " + String.join(", ", missing));
        }
    }

    private Set<String> extractTemplateVariables(String template) {
        if (template == null || template.isBlank()) {
            return Set.of();
        }

        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private String safePromptName(String promptName) {
        return promptName == null || promptName.isBlank() ? "<unnamed>" : promptName;
    }
}
