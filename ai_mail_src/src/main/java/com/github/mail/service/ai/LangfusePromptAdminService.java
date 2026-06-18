package com.github.mail.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.github.mail.config.properties.LangfuseProperties;
import com.github.mail.service.ai.dto.LangfusePromptCreateRequest;
import com.github.mail.service.ai.dto.LangfusePromptDetail;
import com.github.mail.service.ai.dto.LangfusePromptLabelsUpdateRequest;
import com.github.mail.service.ai.dto.LangfusePromptListItem;
import com.github.mail.service.ai.dto.LangfusePromptListResponse;
import com.github.mail.service.ai.dto.LangfusePromptVariablesResponse;
import com.github.mail.service.ai.langfuse.LangfusePromptClient;
import com.github.mail.service.ai.langfuse.LangfusePromptTemplateValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LangfusePromptAdminService {

    private static final String RESERVED_AUTO_LABEL = "latest";

    private final LangfusePromptClient promptClient;
    private final LangfusePromptTemplateValidator promptTemplateValidator;
    private final LangfuseProperties langfuseProperties;

    public LangfusePromptListResponse listPrompts(String name,
                                                  String label,
                                                  String tag,
                                                  Integer page,
                                                  Integer limit) {
        JsonNode response = promptClient.listPrompts(name, label, tag, page, limit);
        JsonNode dataNode = response == null ? NullNode.getInstance() : response.path("data");
        List<LangfusePromptListItem> data = dataNode.isArray()
                ? java.util.stream.StreamSupport.stream(dataNode.spliterator(), false)
                .map(this::toListItem)
                .toList()
                : List.of();
        return new LangfusePromptListResponse(data, copyNode(response == null ? null : response.get("meta")));
    }

    public LangfusePromptDetail getPrompt(String name, Integer version, String label, Boolean resolve) {
        validateVersionAndLabel(version, label);
        return toDetail(promptClient.getPrompt(name, version, label, resolve));
    }

    public LangfusePromptDetail createPrompt(LangfusePromptCreateRequest request) {
        validateLabels(request.labels());
        promptTemplateValidator.validateRequestPrompt(request.name(), request.type(), request.prompt());

        LangfusePromptCreateRequest normalizedRequest = new LangfusePromptCreateRequest(
                request.name(),
                request.type().toLowerCase(Locale.ROOT),
                request.prompt(),
                request.config(),
                normalizeStringList(request.labels()),
                normalizeStringList(request.tags()),
                request.commitMessage()
        );
        return toDetail(promptClient.createPrompt(normalizedRequest));
    }

    public LangfusePromptDetail updatePromptLabels(String name, LangfusePromptLabelsUpdateRequest request) {
        validateLabels(request.newLabels());
        return toDetail(promptClient.updatePromptLabels(name, request.version(), normalizeStringList(request.newLabels())));
    }

    public void deletePrompt(String name, Integer version, String label) {
        validateVersionAndLabel(version, label);
        promptClient.deletePrompt(name, version, label);
    }

    public LangfusePromptVariablesResponse getPromptVariables() {
        return new LangfusePromptVariablesResponse(
                langfuseProperties.getPromptName(),
                langfuseProperties.getPromptLabel(),
                promptTemplateValidator.getReservedVariables(),
                promptTemplateValidator.getOptionalVariables()
        );
    }

    private void validateVersionAndLabel(Integer version, String label) {
        if (version != null && label != null && !label.isBlank()) {
            throw new IllegalArgumentException("version 和 label 不能同时传入");
        }
    }

    private void validateLabels(List<String> labels) {
        if (labels == null) {
            return;
        }
        boolean containsLatest = labels.stream()
                .filter(label -> label != null && !label.isBlank())
                .map(label -> label.toLowerCase(Locale.ROOT))
                .anyMatch(RESERVED_AUTO_LABEL::equals);
        if (containsLatest) {
            throw new IllegalArgumentException("latest 标签由 Langfuse 自动维护，不能手动设置");
        }
    }

    private LangfusePromptListItem toListItem(JsonNode node) {
        return new LangfusePromptListItem(
                readText(node, "name"),
                readText(node, "type"),
                toIntegerList(node.get("versions")),
                toStringList(node.get("labels")),
                toStringList(node.get("tags")),
                readText(node, "lastUpdatedAt"),
                copyNode(node.get("lastConfig"))
        );
    }

    private LangfusePromptDetail toDetail(JsonNode node) {
        return new LangfusePromptDetail(
                readText(node, "name"),
                readText(node, "type"),
                readInteger(node, "version"),
                copyNode(node.get("prompt")),
                copyNode(node.get("config")),
                toStringList(node.get("labels")),
                toStringList(node.get("tags")),
                readText(node, "commitMessage"),
                copyNode(node.get("resolutionGraph"))
        );
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isValueNode)
                .map(JsonNode::asText)
                .toList();
    }

    private List<Integer> toIntegerList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::canConvertToInt)
                .map(JsonNode::asInt)
                .toList();
    }

    private JsonNode copyNode(JsonNode node) {
        return node == null || node.isMissingNode() ? NullNode.getInstance() : node.deepCopy();
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode fieldNode = node == null ? null : node.get(fieldName);
        return fieldNode == null || fieldNode.isNull() ? null : fieldNode.asText();
    }

    private Integer readInteger(JsonNode node, String fieldName) {
        JsonNode fieldNode = node == null ? null : node.get(fieldName);
        return fieldNode == null || fieldNode.isNull() ? null : fieldNode.asInt();
    }
}
