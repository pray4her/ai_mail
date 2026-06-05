package com.github.mail.repo.Mail.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 邮件处理记录实体类 TODO：未启用，可根据实际情况保留是否需要处理记录
 * <p>
 * 设计原则：
 * 1. 异步处理流程支持 - 使用 status 字段追踪处理阶段
 * 2. 软删除支持 - 用 is_deleted 标记，不物理删除
 * 3. 与 MailMessage 解耦 - 处理流程独立，支持重复处理
 * 4. 完整的错误信息保留 - 便于问题诊断
 * 5. 追加写原则 - 新的处理记录直接插入，不更新历史记录
 * 
 * @author Asteries
 * @TableName mail_message
 */
@TableName(value = "mail_processing_record")
@Data
@Builder
public class MailProcessingRecord {

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
     * 关联的邮件账户ID
     */
    @TableField(value = "mail_account_id")
    private Long mailAccountId;

    /**
     * 邮件消息ID快照（冗余存储，便于查询）
     */
    @TableField(value = "message_id_snapshot")
    private String messageIdSnapshot;

    /**
     * 邮件主题快照
     */
    @TableField(value = "subject")
    private String subject;

    /**
     * 发件人快照
     */
    @TableField(value = "from_email")
    private String fromEmail;

    /**
     * 线程ID快照
     */
    @TableField(value = "thread_id")
    private String threadId;

    /**
     * 处理类型
     */
    @TableField(value = "processing_type")
    private String processingType;

    /**
     * 处理版本
     */
    @TableField(value = "processing_version")
    private Integer processingVersion;


    /**
     * 回复生成状态 暂不使用
     * PENDING - 待处理
     * GENERATING - 生成中（AI 模型处理中）
     * GENERATED - 生成完成（回复内容已生成）
     * FAILED - 生成失败
     * DRAFT - 已保存为草稿（发送到邮箱的 AI_reply 文件夹）
     * SENT - 已发送
     */
    @TableField(value = "reply_status")
    private String replyStatus;

    /**
     * AI 生成的回复内容
     */
    @TableField(value = "reply_content")
    private String replyContent;

    /**
     * 回复草稿保存位置
     * 例如：INBOX/AI_reply (表示在该邮箱的 AI_reply 文件夹)
     */
    @TableField(value = "reply_draft_folder")
    private String replyDraftFolder;

    /**
     * 处理者标识
     * 可以是：
     * - AI 模型名称（如 "DeepSeek-V3"）
     * - 用户 ID（如果是人工处理）
     * - 系统标识（如 "SYSTEM_AUTO"）
     */
    @TableField(value = "handled_by")
    private String handledBy;

    /**
     * 处理完成时间
     */
    @TableField(value = "processed_at")
    private LocalDateTime processedAt;

    /**
     * 错误信息
     */
    @TableField(value = "error_message")
    private String errorMessage;

    /**
     * 备注
     */
    @TableField(value = "remarks")
    private String remarks;

    /**
     * 软删除标记
     */
    @TableField(value = "is_deleted")
    private Integer isDeleted;

    /**
     * 记录创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 记录更新时间
     */
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return "MailProcessingRecord{" +
                "id=" + id +
                ", mailMessageId=" + mailMessageId +
                ", mailAccountId=" + mailAccountId +
                ", subject='" + subject + '\'' +
                ", replyStatus='" + replyStatus + '\'' +
                ", processedAt=" + processedAt +
                ", createdAt=" + createdAt +
                '}';
    }
}
