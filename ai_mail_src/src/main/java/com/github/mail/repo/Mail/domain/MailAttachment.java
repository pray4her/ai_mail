package com.github.mail.repo.Mail.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 邮件附件实体类 TODO：此处也未启用，暂未对邮件附件进行解析
 * <p>
 * 设计原则：
 * 1. 只存储附件元数据，不存储二进制内容（节省数据库空间）
 * 2. 支持多种存储后端（本地、S3、OSS 等）
 * 3. content_hash 用于完整性验证和去重
 * 4. is_scanned, is_downloaded 追踪处理状态
 * 
 * @author Asteries
 * @TableName mail_attachment
 */
@TableName(value = "mail_attachment")
@Data
public class MailAttachment {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的邮件消息ID
     */
    @TableField(value = "mail_message_id")
    private Long mailMessageId;

    /**
     * 附件文件名
     */
    @TableField(value = "filename")
    private String filename;

    /**
     * MIME 类型
     * 例如：application/pdf, image/jpeg, text/plain 等
     */
    @TableField(value = "content_type")
    private String contentType;

    /**
     * 附件大小（字节）
     */
    @TableField(value = "content_length")
    private Long contentLength;

    /**
     * SHA-256 哈希值
     * 用于：
     * 1. 完整性验证（确保下载/存储的文件未损坏）
     * 2. 去重（检测同一邮件中的重复附件或不同邮件中的相同附件）
     */
    @TableField(value = "content_hash")
    private String contentHash;

    /**
     * 附件存储路径
     * 例如：s3://bucket/attachments/2025-01/xxxxx.pdf
     *      file:///data/attachments/xxxxx.pdf
     *      oss://bucket/xxxxx.pdf
     */
    @TableField(value = "storage_path")
    private String storagePath;

    /**
     * 存储类型
     * LOCAL（本地文件系统）
     * S3（AWS S3 兼容）
     * OSS（阿里云 OSS）
     * GCS（Google Cloud Storage）
     * AZURE（Azure Blob Storage）
     */
    @TableField(value = "storage_type")
    private String storageType;

    /**
     * 是否已扫描
     * 用于追踪附件是否已进行病毒扫描或安全检查
     */
    @TableField(value = "is_scanned")
    private Integer isScanned;

    /**
     * 是否已下载到本地存储
     * 用于追踪附件是否已从邮件服务器下载并持久化
     */
    @TableField(value = "is_downloaded")
    private Integer isDownloaded;

    /**
     * 记录创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return "MailAttachment{" +
                "id=" + id +
                ", mailMessageId=" + mailMessageId +
                ", filename='" + filename + '\'' +
                ", contentType='" + contentType + '\'' +
                ", contentLength=" + contentLength +
                ", contentHash='" + contentHash + '\'' +
                ", storageType='" + storageType + '\'' +
                ", isDownloaded=" + isDownloaded +
                ", createdAt=" + createdAt +
                '}';
    }
}
