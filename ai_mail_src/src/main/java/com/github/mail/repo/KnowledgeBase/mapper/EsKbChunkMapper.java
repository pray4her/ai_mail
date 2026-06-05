package com.github.mail.repo.KnowledgeBase.mapper;

import com.github.mail.repo.KnowledgeBase.domain.EsKbChunk;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ES 知识库文本块映射器
 * @author Aster
 * @date 2026/1/5
 */
@Component
public class EsKbChunkMapper {

    /**
     * 转换 KbDocumentChunk 为 EsKbChunk
     *
     * @param chunk 文本块
     * @param vectorArray 向量数组
     * @return
     */
    public EsKbChunk toEsChunk(KbDocumentChunk chunk, float[] vectorArray) {
        List<Float> vector = new ArrayList<>(vectorArray.length);
        for (float f : vectorArray) {
            vector.add(f);
        }

        return EsKbChunk.builder()
                .chunkId(chunk.getId())
                .documentId(chunk.getDocumentId())
                .chunkIndex(chunk.getChunkIndex())
                .textContent(chunk.getTextContent())
                .textVector(vector)
                .tokenCount(chunk.getTokenCount())
                .createdAt(System.currentTimeMillis())
                .build();
    }
}