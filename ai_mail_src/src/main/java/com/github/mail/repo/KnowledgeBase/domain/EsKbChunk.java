package com.github.mail.repo.KnowledgeBase.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Elasticsearch 知识库分片文档
 * <p>
 * 对应 ES Index: kb_chunks
 * 
 * @author Aster
 * @date 2025/12/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsKbChunk {
    
    /**
     * 文档 ID (对应 MySQL chunk_id)
     */
    @JsonProperty("chunk_id")
    private Long chunkId;
    
    /**
     * 所属文档 ID
     */
    @JsonProperty("document_id")
    private Long documentId;
    
    /**
     * 分片序号
     */
    @JsonProperty("chunk_index")
    private Integer chunkIndex;
    
    /**
     * 文本内容 (用于 BM25 检索)
     */
    @JsonProperty("text_content")
    private String textContent;
    
    /**
     * 向量 (用于 knn 检索)
     * 注意: ES 的 dense_vector 存储为 float[]
     */
    @JsonProperty("text_vector")
    private List<Float> textVector;
    
    /**
     * Token 数量
     */
    @JsonProperty("token_count")
    private Integer tokenCount;
    
    /**
     * 创建时间戳 (毫秒)
     */
    @JsonProperty("created_at")
    private Long createdAt;
}
