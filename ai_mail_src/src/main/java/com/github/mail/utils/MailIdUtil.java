package com.github.mail.utils;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/**
 * 邮件ID工具类
 * 用于处理和解析邮件ID相关操作
 * @author Aster
 * @date 2025/12/25
 */
@Slf4j
public class MailIdUtil {
    
    /**
     * 从邮件消息中获取唯一标识符
     * 优先级: Message-ID Header > Content-ID > Subject + Date 组合
     */
    public static String getUniqueEmailId(Message message) {
        try {
            // 优先使用Message-ID头
            String[] messageIdHeaders = message.getHeader("Message-ID");
            if (messageIdHeaders != null && messageIdHeaders.length > 0) {
                String messageId = messageIdHeaders[0];
                // 清理Message-ID格式，移除尖括号
                messageId = messageId.replaceAll("[<>]", "").trim();
                if (!messageId.isEmpty()) {
                    return messageId;
                }
            }
            
            // 备选方案：使用Content-ID
            String[] contentIdHeaders = message.getHeader("Content-ID");
            if (contentIdHeaders != null && contentIdHeaders.length > 0) {
                String contentId = contentIdHeaders[0];
                contentId = contentId.replaceAll("[<>]", "").trim();
                if (!contentId.isEmpty()) {
                    return contentId;
                }
            }
            
            // 最后备选：基于主题和日期生成ID
            String subject = message.getSubject();
            String date = message.getSentDate() != null ? 
                message.getSentDate().toString() : 
                message.getReceivedDate().toString();
            
            if (subject != null && date != null) {
                // 使用主题和日期的哈希值作为唯一ID
                String combined = subject + date;
                return String.valueOf(combined.hashCode());
            }
            
            // 如果以上都失败，返回消息对象的哈希码
            return String.valueOf(message.hashCode());
            
        } catch (MessagingException e) {
            log.warn("获取邮件ID时出错", e);
            return String.valueOf(message.hashCode());
        }
    }
    
    /**
     * 检查邮件是否包含指定的ID
     */
    public static boolean containsEmailId(Message message, String targetId) {
        String uniqueId = getUniqueEmailId(message);
        return targetId.equals(uniqueId);
    }
    
    /**
     * 获取邮件的引用ID（用于回复邮件时识别原邮件）
     */
    public static String getInReplyToId(Message message) {
        try {
            String[] inReplyToHeaders = message.getHeader("In-Reply-To");
            if (inReplyToHeaders != null && inReplyToHeaders.length > 0) {
                return inReplyToHeaders[0].replaceAll("[<>]", "").trim();
            }
            return null;
        } catch (MessagingException e) {
            log.warn("获取邮件回复引用ID时出错", e);
            return null;
        }
    }
    
    /**
     * 获取邮件的引用链（References header）
     */
    public static String[] getReferences(Message message) {
        try {
            String[] references = message.getHeader("References");
            if (references != null) {
                // 分割引用ID列表
                String[] ids = references[0].split("\\s+");
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = ids[i].replaceAll("[<>]", "").trim();
                }
                return ids;
            }
            return new String[0];
        } catch (MessagingException e) {
            log.warn("获取邮件引用链时出错", e);
            return new String[0];
        }
    }
}