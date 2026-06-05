package com.github.mail.repo.KnowledgeBase.domain;

import lombok.Getter;

/**
 * 中间类，用于存储和表示具有相似度评分的 chunk
 * @author Aster
 * @date 2025/12/29
 */
@Getter
public class ScoredChunk {

    // 原始 chunk 对象
    private final KbVectorIndex chunk;
    // 与 query 的相似度
    private final double score;

    public ScoredChunk(KbVectorIndex chunk, double score) {
        this.chunk = chunk;
        this.score = score;
    }

    @Override
    public String toString() {
        return "ScoredChunk{" +
                "chunkId=" + chunk.getChunkId() +
                ", score=" + score +
                ", text='" + chunk.getEmbeddingVector() + '\'' +
                '}';
    }
}

