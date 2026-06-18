package com.github.mail.service.ai.langfuse;

import com.github.mail.service.ai.AiGenerationRequest;
import com.langfuse.client.resources.commons.types.CreateScoreValue;
import com.langfuse.client.resources.score.types.CreateScoreRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangfuseEvaluationService {

    private final LangfuseClientFactory clientFactory;

    public void recordHeuristicScores(String traceId, AiGenerationRequest request, String output) {
        clientFactory.getClient().ifPresent(client -> {
            createScore(client, traceId, "generation_success", output != null && !output.isBlank() ? 1.0 : 0.0,
                    "输出是否为空");
            createScore(client, traceId, "rag_context_available", request.ragChunks().isEmpty() ? 0.0 : 1.0,
                    "本次回复是否命中知识库片段");
            createScore(client, traceId, "mentions_ai_or_kb", containsSensitiveDisclosure(output) ? 0.0 : 1.0,
                    "是否避免暴露 AI 或知识库来源");
        });
    }

    private void createScore(com.langfuse.client.LangfuseClient client,
                             String traceId,
                             String name,
                             double value,
                             String comment) {
        try {
            client.score().create(
                    CreateScoreRequest.builder()
                            .name(name)
                            .value(CreateScoreValue.of(value))
                            .traceId(traceId)
                            .comment(comment)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Langfuse score 写入失败: traceId={}, name={}", traceId, name, e);
        }
    }

    private boolean containsSensitiveDisclosure(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        String lower = output.toLowerCase();
        return lower.contains("ai") || output.contains("知识库");
    }
}
