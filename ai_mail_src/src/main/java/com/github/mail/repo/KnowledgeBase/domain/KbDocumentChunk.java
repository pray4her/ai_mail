package com.github.mail.repo.KnowledgeBase.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库文档分片实体类
 * <p>
 * 设计原则：
 * 1. 仅用于知识库文档的分片（不对邮件分片）
 * 2. 记录分片元数据（序号、内容、token 数）
 * 3. chunk_md5 用于内容去重
 * 4. 与文档的关系：一个文档可以有多个分片
 * 
 * @author Asteries
 * @TableName kb_document_chunk
 */
@TableName(value = "kb_document_chunk")
@Data
public class KbDocumentChunk {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 所属文档ID
     */
    @TableField(value = "document_id")
    private Long documentId;
    
    /**
     * 分片序号（0-based）
     * 用于维护分片顺序
     */
    @TableField(value = "chunk_index")
    private Integer chunkIndex;
    
    /**
     * 分片内容指纹（MD5）
     * 用于检测重复的 chunk
     */
    @TableField(value = "chunk_md5")
    private String chunkMd5;
    
    /**
     * 原始文本内容
     * 这是分片后的文本，用于向量化
     */
    @TableField(value = "text_content")
    private String textContent;
    
    /**
     * Token 数量估算
     * 用于评估向量化成本和上下文窗口占用
     */
    @TableField(value = "token_count")
    private Integer tokenCount;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
