package com.github.mail.client;

import com.aliyun.bailian20231229.models.RetrieveResponse;
import com.aliyun.bailian20231229.models.RetrieveResponseBody;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 百炼 Retrieve 响应映射工具
 *
 * @author System
 */
@Slf4j
public final class BailianRetrieveMapper {

    private BailianRetrieveMapper() {
    }

    public static List<RagChunk> toRagChunks(RetrieveResponse response, int topK, double minScore) {
        if (response == null || response.getBody() == null) {
            return Collections.emptyList();
        }

        RetrieveResponseBody body = response.getBody();
        if (!Boolean.TRUE.equals(body.getSuccess()) && !"Success".equalsIgnoreCase(body.getCode())) {
            log.warn("百炼检索返回失败: code={}, message={}", body.getCode(), body.getMessage());
            return Collections.emptyList();
        }

        if (body.getData() == null || body.getData().getNodes() == null) {
            return Collections.emptyList();
        }

        List<RagChunk> chunks = new ArrayList<>();
        for (RetrieveResponseBody.RetrieveResponseBodyDataNodes node : body.getData().getNodes()) {
            if (node == null || node.getText() == null || node.getText().isBlank()) {
                continue;
            }
            double score = node.getScore() != null ? node.getScore() : 0.0;
            if (score < minScore) {
                continue;
            }
            chunks.add(new RagChunk(node.getText(), score, resolveChunkId(node)));
        }

        return chunks.stream()
                .limit(Math.max(topK, 0))
                .collect(Collectors.toList());
    }

    static String resolveChunkId(RetrieveResponseBody.RetrieveResponseBodyDataNodes node) {
        Object metadataObj = node.getMetadata();
        if (metadataObj instanceof Map<?, ?> metadata) {
            Object id = metadata.get("_id");
            if (id != null) {
                return String.valueOf(id);
            }
            Object docId = metadata.get("doc_id");
            if (docId != null) {
                return String.valueOf(docId);
            }
        }
        return "bailian-" + System.identityHashCode(node);
    }
}
