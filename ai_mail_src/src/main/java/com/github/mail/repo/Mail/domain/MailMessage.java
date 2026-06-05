package com.github.mail.repo.Mail.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

/**
 * 邮件消息主表实体类 TODO：邮件入库则需启用
 * <p>
 * 设计原则：
 * 1. message_id 是全局唯一标识，保证幂等性
 * 2. content_hash 用于去重检查
 * 3. 分离 HTML 和纯文本内容，便于多格式处理
 * 4. 软删除支持（is_deleted）
 * 5. 完整保留 raw_headers 用于诊断和信息提取
 * 6. 支持会话追踪（thread_id, in_reply_to, mail_references）
 * 
 * @author Asteries
 * @TableName mail_message
 */
@TableName(value ="mail_message", autoResultMap = true)
@Data
public class MailMessage {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属邮箱账户ID
     */
    @TableField(value = "mail_account_id")
    private Long mailAccountId;

    /**
     * 邮件全局唯一标识（Message-ID header）
     * 用于幂等性保证：同一邮件即使重复拉取，也只会插入一次
     */
    @TableField(value = "message_id")
    private String messageId;

    /**
     * 内容哈希值（SHA-256）
     * 用于快速去重检查：检测内容相同但 message_id 不同的邮件
     */
    @TableField(value = "content_hash")
    private String contentHash;


    /**
     * 邮件主题
     */
    @TableField(value = "subject")
    private String subject;

    /**
     * 发件人邮箱地址
     */
    @TableField(value = "from_email")
    private String fromEmail;

    /**
     * 发件人名称
     */
    @TableField(value = "from_name")
    private String fromName;

    /**
     * 收件人列表（JSON 格式）
     * 例如：["user1@example.com", "user2@example.com"]
     */
    @TableField(value = "to_emails", typeHandler = JacksonTypeHandler.class)
    private List<String> toEmails;

    /**
     * 抄送人列表（JSON 格式）
     */
    @TableField(value = "cc_emails", typeHandler = JacksonTypeHandler.class)
    private List<String> ccEmails;

    /**
     * 密送人列表（JSON 格式）
     */
    @TableField(value = "bcc_emails", typeHandler = JacksonTypeHandler.class)
    private List<String> bccEmails;

    @TableField("in_reply_to")
    private String inReplyTo;

    @TableField("mail_references")
    private String mailReferences;

    /**
     * 邮件线程ID
     * 用于将相关的邮件分组在一起（会话化）
     */
    @TableField(value = "thread_id")
    private String threadId;

    /**
     * HTML 格式的邮件正文
     */
    @TableField(value = "body_html")
    private String bodyHtml;

    /**
     * 纯文本格式的邮件正文
     * 后续的向量化、分片操作主要基于此字段
     */
    @TableField(value = "body_text")
    private String bodyText;

    /**
     * 是否有附件
     */
    @TableField(value = "has_attachment")
    private Integer hasAttachment;

    /**
     * 附件数量
     */
    @TableField(value = "attachment_count")
    private Integer attachmentCount;


    /**
     * 是否已读
     */
    @TableField(value = "is_read")
    private Integer isRead;


    /**
     * 是否已标记（IMAP flag）
     */
    @TableField(value = "is_flagged")
    private Integer isFlagged;

    /**
     * 软删除标记
     */
    @TableField(value = "is_deleted")
    private Integer isDeleted;


    /**
     * 邮件发送时间
     */
    @TableField(value = "sent_at")
    private LocalDateTime sentAt;

    /**
     * 邮件接收时间
     */
    @TableField(value = "received_at")
    private LocalDateTime receivedAt;

    /**
     * 原始邮件头
     * 保留完整的 SMTP 头信息，用于诊断和提取高级信息
     */
    @TableField(value = "raw_headers")
    private String rawHeaders;

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
        return "MailMessage{" +
                "id=" + id +
                ", mailAccountId=" + mailAccountId +
                ", messageId='" + messageId + '\'' +
                ", subject='" + subject + '\'' +
                ", fromEmail='" + fromEmail + '\'' +
                ", threadId='" + threadId + '\'' +
                ", hasAttachment=" + hasAttachment +
                ", isDeleted=" + isDeleted +
                ", sentAt=" + sentAt +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        MailMessage other = (MailMessage) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getMailAccountId() == null ? other.getMailAccountId() == null : this.getMailAccountId().equals(other.getMailAccountId()))
            && (this.getMessageId() == null ? other.getMessageId() == null : this.getMessageId().equals(other.getMessageId()))
            && (this.getContentHash() == null ? other.getContentHash() == null : this.getContentHash().equals(other.getContentHash()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getMailAccountId() == null) ? 0 : getMailAccountId().hashCode());
        result = prime * result + ((getMessageId() == null) ? 0 : getMessageId().hashCode());
        result = prime * result + ((getContentHash() == null) ? 0 : getContentHash().hashCode());
        return result;
    }
}