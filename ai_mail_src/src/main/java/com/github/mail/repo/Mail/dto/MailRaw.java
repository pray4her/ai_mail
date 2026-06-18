package com.github.mail.repo.Mail.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 原始邮件实体，用于从邮件服务器拉取的原始数据
 * 在转换为 MailMessage 实体时使用
 *
 * @author Aster
 * @date 2025/12/24
 */
@Data
public class MailRaw {

    /** 邮件唯一 ID，对应 MailMessage.messageId */
    private String messageId;

    /** 邮件线程 ID，用于关联同一会话的邮件，对应 MailMessage.threadId */
    private String threadId;

    /** 邮件主题，对应 MailMessage.subject */
    private String subject;

    /** 发件人邮箱，对应 MailMessage.fromEmail */
    private String from;

    /** 收件人列表，对应 MailMessage.toEmails，最终会转成 JSON 字符串 */
    private List<String> to;

    /** 邮件发送时间，对应 MailMessage.sentAt */
    private Date sentDate;

    /** 邮件纯文本内容，对应 MailMessage.bodyText；若为空，则从 HTML 中提取文本 */
    private String textBody;

    /** 邮件 HTML 内容，对应 MailMessage.bodyHtml */
    private String htmlBody;

    /** 是否包含附件，对应 MailMessage.hasAttachment（1 表示有附件，0 表示无附件） */
    private boolean hasAttachment;

    private int attachmentCount;

    private List<MailRawAttachment> attachments;
}

