package com.github.mail.client;

import com.aliyun.bailian20231229.models.RetrieveResponse;
import com.aliyun.bailian20231229.models.RetrieveResponseBody;
import com.aliyun.bailian20231229.models.RetrieveResponseBody.RetrieveResponseBodyData;
import com.aliyun.bailian20231229.models.RetrieveResponseBody.RetrieveResponseBodyDataNodes;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BailianRetrieveMapperTest {

    @Test
    void toRagChunks_filtersByMinScoreAndTopK() {
        RetrieveResponse response = buildResponse(
                node("chunk-a", 0.9, "_id", "id-a"),
                node("chunk-b", 0.4, "_id", "id-b"),
                node("chunk-c", 0.8, "_id", "id-c")
        );

        List<RagChunk> chunks = BailianRetrieveMapper.toRagChunks(response, 2, 0.5);

        assertEquals(2, chunks.size());
        assertEquals("chunk-a", chunks.get(0).getChunkText());
        assertEquals("chunk-c", chunks.get(1).getChunkText());
    }

    @Test
    void toRagChunks_returnsEmptyWhenApiFailed() {
        RetrieveResponse response = new RetrieveResponse();
        RetrieveResponseBody body = new RetrieveResponseBody();
        body.setSuccess(false);
        body.setCode("InternalError");
        response.setBody(body);

        List<RagChunk> chunks = BailianRetrieveMapper.toRagChunks(response, 5, 0.1);

        assertTrue(chunks.isEmpty());
    }

    @Test
    void resolveChunkId_usesDocIdWhenUnderscoreIdMissing() {
        RetrieveResponseBodyDataNodes node = node("text", 0.7, "doc_id", "doc-123");

        String chunkId = BailianRetrieveMapper.resolveChunkId(node);

        assertEquals("doc-123", chunkId);
    }

    private RetrieveResponse buildResponse(RetrieveResponseBodyDataNodes... nodes) {
        RetrieveResponse response = new RetrieveResponse();
        RetrieveResponseBody body = new RetrieveResponseBody();
        body.setSuccess(true);
        body.setCode("Success");

        RetrieveResponseBodyData data = new RetrieveResponseBodyData();
        data.setNodes(List.of(nodes));
        body.setData(data);
        response.setBody(body);
        return response;
    }

    private RetrieveResponseBodyDataNodes node(String text, double score, String metaKey, String metaValue) {
        RetrieveResponseBodyDataNodes node = new RetrieveResponseBodyDataNodes();
        node.setText(text);
        node.setScore(score);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(metaKey, metaValue);
        node.setMetadata(metadata);
        return node;
    }
}
