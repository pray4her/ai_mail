package com.github.mail.service.MailOperation.impl;

import com.github.mail.model.config.MailConfig;
import com.github.mail.model.config.Properties.MailServerProperties;
import com.github.mail.service.Config.ConfigService;
import com.github.mail.service.MailOperation.MailSendService;
import com.github.mail.utils.MailConnectUtil;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * IMAP邮件发送和操作服务实现
 * 用于存储AI生成的回复草稿到AI_reply文件夹
 *
 * @author Asteries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSendServiceImpl implements MailSendService {

    @Override
    public boolean saveDraftToFolder(String recipientEmail,
                                     String subject,
                                     String content,
                                     String folderName,
                                     MailConfig.Imap imapConfig) {

        MailServerProperties.Imap imapProperties =
                MailServerProperties.fromMailConfig(imapConfig);

        try (Store store = MailConnectUtil.connect(imapProperties)) {
            // 创建文件夹（如果不存在）
            Folder folder = createOrGetFolder(store, folderName);

            // 创建邮件
            Session session = Session.getInstance(new java.util.Properties());
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(imapProperties.getUserName()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
            message.setSubject("Re: " + subject, "UTF-8");
            message.setText(content, "UTF-8", "plain");
            message.setSentDate(new java.util.Date());

            // 将邮件追加到文件夹
            if (folder.isOpen()) {
                folder.close(false);
            }
            folder.open(Folder.READ_WRITE);
            folder.appendMessages(new Message[]{message});
            folder.close(false);

            log.info("邮件草稿已保存到 {} 文件夹: 收件人={}, 主题={}", folderName, recipientEmail, subject);
            return true;

        } catch (Exception e) {
            log.error("保存邮件草稿失败: folderName={}, recipientEmail={}", folderName, recipientEmail, e);
            return false;
        }
    }


    /**
     * 创建或获取文件夹
     */
    private Folder createOrGetFolder(Store store, String folderName) throws MessagingException {
        Folder folder = store.getFolder(folderName);

        if (!folder.exists()) {
            if (folder.create(Folder.HOLDS_MESSAGES)) {
                log.info("已创建文件夹: {}", folderName);
            } else {
                log.warn("无法创建文件夹: {}, 使用默认INBOX", folderName);
                folder = store.getFolder("INBOX");
            }
        }

        return folder;
    }
}
