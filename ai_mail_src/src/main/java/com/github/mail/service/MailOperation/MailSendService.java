package com.github.mail.service.MailOperation;

import com.github.mail.model.config.MailConfig;
import jakarta.mail.Folder;
import jakarta.mail.Store;

/**
 * IMAP邮件发送和操作服务
 * 用于在AI_reply文件夹存储AI生成的回复草稿
 * @author Asteries
 */
public interface MailSendService {

    /**
     * 将AI生成的回复存储为邮件草稿到指定文件夹
     * @param recipientEmail 原邮件发送者
     * @param subject 邮件主题（自动添加Re:前缀）
     * @param content 邮件内容
     * @param folderName 存储文件夹名称（如AI_reply）
     * @return 是否成功
     */
    boolean saveDraftToFolder(String recipientEmail, String subject, String content, String folderName, MailConfig.Imap imapConfig);


}
