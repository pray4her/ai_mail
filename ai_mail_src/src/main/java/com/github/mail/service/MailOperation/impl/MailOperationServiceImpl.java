package com.github.mail.service.MailOperation.impl;

import com.github.mail.model.config.MailConfig;
import com.github.mail.model.config.Properties.MailServerProperties;
import com.github.mail.service.MailOperation.MailOperationService;
import com.github.mail.utils.MailConnectUtil;
import com.github.mail.utils.MailIdUtil;
import com.github.mail.utils.MailSecurityUtil;
import jakarta.mail.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 邮件操作服务实现 TODO：未启用，保留
 *
 * @author Aster
 * @date 2025/12/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailOperationServiceImpl implements MailOperationService {

    private final MailConfig mailConfig;


    @Override
    public void moveMail(String emailId, String targetFolder) {
        executeMailOperation(emailId,
                (folder, message) -> moveMessage(folder, message, targetFolder)
        );
    }

    @Override
    public void moveMails(List<String> emailIdList, String targetFolder) {
        executeMailOperationBatch(emailIdList,
                (folder, message) -> moveMessage(folder, message, targetFolder)
        );
    }


    private void moveMessage(Folder source, Message message, String targetFolder) throws MessagingException {
        Folder target = getOrCreateFolder(source.getStore(), targetFolder);
        try {
            //target.open(Folder.READ_WRITE); 这里会触发风控，不能open Inbox以外的文件夹
            // 建议使用 appendMessages 而不是 copy，对于某些网易服务器更稳定
            source.copyMessages(new Message[]{message}, target);
            message.setFlag(Flags.Flag.DELETED, true);
        } finally {
            if (target != null && target.isOpen()) {
                // 只关闭目标文件夹
                target.close(false);
            }
            // 不要在这里关闭 source，让外层 executeMailOperation 统一处理
        }
    }

    @Override
    public void copyMail(String emailId, String targetFolder) {
        executeMailOperation(emailId, (folder, message) -> {
            // 打开目标文件夹（如果不存在则创建）
            Folder target = getOrCreateFolder(folder.getStore(), targetFolder);

            // 复制邮件到目标文件夹
            folder.copyMessages(new Message[]{message}, target);

            // 关闭文件夹
            target.close(false);
        });
    }

    @Override
    public void markAsRead(String emailId) {
        executeMailOperation(emailId, (folder, message) -> {
            message.setFlag(Flags.Flag.SEEN, true);
        });
    }

    @Override
    public void markAsUnread(String emailId) {
        executeMailOperation(emailId, (folder, message) -> {
            message.setFlag(Flags.Flag.SEEN, false);
        });
    }

    @Override
    public void markAsReplied(String emailId) {
        executeMailOperation(emailId, (folder, message) -> {
            message.setFlag(Flags.Flag.ANSWERED, true);
        });
    }

    @Override
    public void markAsDeleted(String emailId) {
        executeMailOperation(emailId, (folder, message) -> {
            message.setFlag(Flags.Flag.DELETED, true);
        });
    }

    @Override
    public void addLabel(String emailId, String label) {
        executeMailOperation(emailId, (folder, message) -> {
            // Gmail等支持关键词的邮箱可以使用关键词作为标签
            if (message instanceof jakarta.mail.internet.MimeMessage) {
                // 设置关键词（标签）
                message.setFlags(new Flags(label), true);
            }
        });
    }

    @Override
    public void removeLabel(String emailId, String label) {
        executeMailOperation(emailId, (folder, message) -> {
            // 移除关键词（标签）
            if (message instanceof jakarta.mail.internet.MimeMessage) {
                message.setFlags(new Flags(label), false);
            }
        });
    }

    /**
     * 批量执行邮件操作
     * @param emailIds 邮件ID列表
     * @param operation 要执行的操作
     */
    private void executeMailOperationBatch(List<String> emailIds, MailOperation operation) {
        emailIds.forEach(emailID -> {
            executeMailOperation(emailID, operation);
        });
    }


    /**
     * 在指定邮件上执行操作的通用方法
     */
    private void executeMailOperation(String emailId, MailOperation operation) {


        MailConfig.Imap imapConfig =
                mailConfig.getImapList().get(0);

        MailServerProperties.Imap imapProperties =
                MailServerProperties.fromMailConfig(imapConfig);


        try (Store store = MailConnectUtil.connect(imapProperties)) {

            // 尝试在INBOX中查找邮件
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // 通过消息ID查找邮件
            Message[] messages = inbox.getMessages();
            Message targetMessage = null;
            Folder sourceFolder = inbox;

            // 在INBOX中搜索
            for (Message message : messages) {
                if (MailIdUtil.containsEmailId(message, emailId)) {
                    targetMessage = message;
                    break;
                }
            }

            if (targetMessage != null) {
                operation.execute(sourceFolder, targetMessage);

                // 更新数据库中的邮件状态 暂不入库
                //updateMailStatusInDatabase(emailId);

                log.info("Successfully executed operation on email: {}", emailId);
            } else {
                log.warn("Email not found: {}", emailId);
                throw new RuntimeException("Email not found: " + emailId);
            }

        } catch (Exception e) {
            String errorMessage = e.getMessage();
            log.error("Unsafe login or authentication failed for email: {}. This may be due to security restrictions by the mail server. Please check your credentials or use app-specific password. Error: {}", emailId, errorMessage);

            // 检查错误消息是否包含安全相关关键词
            if (errorMessage.toLowerCase().contains("unsafe login") ||
                    errorMessage.toLowerCase().contains("security") ||
                    errorMessage.toLowerCase().contains("authentication")) {

                String securityRecommendation = MailSecurityUtil.getSecurityRecommendation(imapProperties);
                log.error("Security recommendation: {}", securityRecommendation);

                throw new RuntimeException("Unsafe login. Please check your email provider's security settings and use app-specific password. See logs for details.", e);
            }

            throw new RuntimeException("Command failed: " + errorMessage, e);
        }
    }



    /**
     * 获取或创建文件夹
     */
    private Folder getOrCreateFolder(Store store, String folderName) throws MessagingException {
        Folder folder = store.getFolder(folderName);

        if (!folder.exists()) {
            // 尝试创建文件夹
            if (folder.create(Folder.HOLDS_MESSAGES)) {
                log.info("Created folder: {}", folderName);
            } else {
                // 如果创建失败，使用默认的INBOX
                folder = store.getFolder("INBOX");
            }
        }

        return folder;
    }

    @FunctionalInterface
    private interface MailOperation {
        void execute(Folder folder, Message message) throws MessagingException;
    }


}