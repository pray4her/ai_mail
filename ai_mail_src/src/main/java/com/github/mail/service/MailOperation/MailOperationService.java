package com.github.mail.service.MailOperation;

import java.util.List;

/**
 * 邮件状态改变服务接口
 *
 * @author Aster
 * @date 2025/12/25
 */
public interface MailOperationService {

    /**
     * 移动邮件到指定文件夹
     *
     * @param emailId      邮件ID
     * @param targetFolder 目标文件夹
     */
    void moveMail(String emailId, String targetFolder);

    /**
     * 移动多封邮件到指定文件夹
     *
     * @param emailIdList     邮件ID数组
     * @param targetFolder 目标文件夹
     */
    void moveMails(List<String> emailIdList, String targetFolder);

    /**
     * 复制邮件到指定文件夹
     *
     * @param emailId      邮件ID
     * @param targetFolder 目标文件夹
     */
    void copyMail(String emailId, String targetFolder);

    /**
     * 标记邮件为已读
     *
     * @param emailId 邮件ID
     */
    void markAsRead(String emailId);

    /**
     * 标记邮件为未读
     *
     * @param emailId 邮件ID
     */
    void markAsUnread(String emailId);

    /**
     * 标记邮件为已回复
     *
     * @param emailId 邮件ID
     */
    void markAsReplied(String emailId);

    /**
     * 标记邮件为已删除（设置删除标志）
     *
     * @param emailId 邮件ID
     */
    void markAsDeleted(String emailId);

    /**
     * 为邮件添加标签/关键词
     *
     * @param emailId 邮件ID
     * @param label   标签名称
     */
    void addLabel(String emailId, String label);

    /**
     * 从邮件移除标签/关键词
     *
     * @param emailId 邮件ID
     * @param label   标签名称
     */
    void removeLabel(String emailId, String label);
}