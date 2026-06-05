package com.github.mail.repo.KbDocument.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库文档实体类
 * <p>
 * 设计原则：
 * 1. 只有知识库文档才会进行分片和向量化
 * 2. 使用 Apache Tika 解析各种格式文档（PDF、Word、Markdown 等）
 * 3. 状态机：上传中 → 已解析 → 已向量化
 * 4. file_md5 保证文档去重
 * 
 * @author Asteries
 * @TableName kb_document
 */
@TableName(value = "kb_document")
@Data
public class KbDocument {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 文件指纹（MD5）
     * 用于去重：相同内容的文档只保留一份
     */
    @TableField(value = "file_md5")
    private String fileMd5;
    
    /**
     * 文件名
     */
    @TableField(value = "file_name")
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    @TableField(value = "total_size")
    private Long totalSize;


    /**
     * minio桶名称
     */
    @TableField(value = "bucket_name")
    private String bucketName;

    /**
     * 源文件路径
     */
    @TableField(value = "raw_object_key")
    private String rawObjectKey;

    /**
     * 解析文件路径
     */
    @TableField(value = "parsed_object_key")
    private String parsedObjectKey;



    /**
     * 文档处理状态
     * 0 = 上传中
     * 1 = 已解析（Tika 解析完成）(完成分片)
     * 2 = 已向量化（embedding 完成）
     * 9 = 失败
     */
    @TableField(value = "status")
    private Integer status;
    
    /**
     * 所属用户ID
     */
    @TableField(value = "user_id")
    private String userId;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * 解析完成时间
     */
    @TableField(value = "parsed_at")
    private LocalDateTime parsedAt;
    
    /**
     * 向量化完成时间
     */
    @TableField(value = "vectorized_at")
    private LocalDateTime vectorizedAt;
}
