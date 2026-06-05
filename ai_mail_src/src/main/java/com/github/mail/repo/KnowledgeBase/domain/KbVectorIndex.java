package com.github.mail.repo.KnowledgeBase.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库向量索引映射实体类
 * <p>
 * 设计原则：
 * 1. 记录 chunk 与向量库的映射关系
 * 2. 向量实际存储在外部向量库（Milvus/Pinecone）
 * 3. embedding_id 是向量库中的唯一标识
 * 4. 支持多模型（不同的 embedding 模型可以共存）
 * 
 * @author Asteries
 * @TableName kb_vector_index
 */
@TableName(value = "kb_vector_index")
@Data
public class KbVectorIndex {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 关联的 chunk 主键
     */
    @TableField(value = "chunk_id")
    private Long chunkId;
    
    /**
     * 向量库中的 ID
     * 例如：Milvus 中的 primary key 或 Pinecone 中的 vector ID
     */
    @TableField(value = "embedding_id")
    private String embeddingId;


    /**
     * 向量数据（JSON 格式）
     * 例如：Milvus 中的向量数据 暂时不存数据库
     */
    @TableField(value = "embedding_vector")
    private String embeddingVector;
    
    /**
     * Embedding 模型版本
     * 例如：text-embedding-3-small, bge-base-zh-v1.5
     * 支持多模型：同一个 chunk 可以有多个模型的 embedding
     */
    @TableField(value = "model_version")
    private String modelVersion;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
