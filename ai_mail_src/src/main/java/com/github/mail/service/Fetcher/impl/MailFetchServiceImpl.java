package com.github.mail.service.Fetcher.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.mail.model.config.MailConfig;
import com.github.mail.utils.MailConnectUtil;
import com.github.mail.utils.MailIdUtil;
import com.github.mail.model.config.Properties.MailServerProperties;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.domain.MailAccount;
import com.github.mail.repo.Mail.mapper.MailAccountMapper;
import com.github.mail.service.Fetcher.MailFetchService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 用户邮件拉取服务实现
 * @author Aster
 * @date 2025/12/24
 */

//TODO：实现ThreadID保存会话(上下文)
@Slf4j
@Service
@RequiredArgsConstructor
public class MailFetchServiceImpl implements MailFetchService {


    private final MailAccountMapper mailAccountMapper;


    @Override
    public List<MailRaw> fetchToAiReply(MailConfig.Imap imapConfig) {

        MailServerProperties.Imap config =
                MailServerProperties.fromMailConfig(imapConfig);

        List<MailRaw> result = new ArrayList<>();

        try (Store store = MailConnectUtil.connect(config)) {

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Long lastSyncUid = getLastSyncUid(config);

            if (inbox instanceof UIDFolder uidFolder) {

                long uidValidity = uidFolder.getUIDValidity();
                log.info("UIDValidity: {}", uidValidity);

                // ========= 第一次同步（没有 lastSyncUid） =========
                // TODO：第一次同步不回复邮件 直接保存账户信息
                if (lastSyncUid == null || lastSyncUid <= 0) {

                    int total = inbox.getMessageCount();
                    if (total == 0) {
                        inbox.close(false);
                        return result;
                    }

                    int FETCH_SIZE = 10;
                    int start = Math.max(1, total - FETCH_SIZE + 1);

                    Message[] recentMessages = inbox.getMessages(start, total);

                    long maxUid = 0;
                    for (Message message : recentMessages) {
                        long uid = uidFolder.getUID(message);
                        maxUid = Math.max(maxUid, uid);
                        result.add(parseMessage(message));
                    }

                    if (maxUid > 0) {
                        updateUidConfig(config, maxUid, uidValidity);
                    }

                    inbox.close(false);
                    //对于新用户返回空列表则表示不生成回复邮件（简单实现）(未测试)
                    return Collections.emptyList();
                    //return result;
                }

                // ========= 正常增量同步 =========
                log.info("LastSyncUid: {}", lastSyncUid);
                log.info("FolderLastUID: {}",UIDFolder.LASTUID);

                Message[] newMessages =
                        uidFolder.getMessagesByUID(lastSyncUid + 1, UIDFolder.LASTUID);

                if (newMessages == null || newMessages.length == 0) {
                    inbox.close(false);
                    return result;
                }

                long maxUid = lastSyncUid;

                //  防止一次拉太多
                int LIMIT = 50;
                int count = Math.min(newMessages.length, LIMIT);

                for (int i = 0; i < count; i++) {
                    Message message = newMessages[i];
                    long uid = uidFolder.getUID(message);

                    if (uid <= lastSyncUid) {
                        continue;
                    }

                    maxUid = Math.max(maxUid, uid);
                    result.add(parseMessage(message));
                }

                if (maxUid > lastSyncUid) {
                    updateUidConfig(config, maxUid, uidValidity);
                }

            } else {
                log.warn("Folder is not UIDFolder, fallback to recent messages");

                int total = inbox.getMessageCount();
                int start = Math.max(1, total - 4);
                Message[] messages = inbox.getMessages(start, total);

                for (Message message : messages) {
                    result.add(parseMessage(message));
                }
            }

            inbox.close(false);

        } catch (Exception e) {
            log.error("拉取邮件失败", e);
        }

        return result;
    }



    //TODO：用于测试邮箱移动功能，未启用
    @Override
    public List<String> fetchInboxIds(MailConfig.Imap imapConfig) {


        MailServerProperties.Imap config =
                MailServerProperties.fromMailConfig(imapConfig);

        try (Store store = MailConnectUtil.connect(config)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            // 获取当前邮箱账户的最后同步UID
            Long lastSyncUid = getLastSyncUid(config);

            List<String> result = new ArrayList<>();

            // 将Folder转换为UIDFolder以使用UID功能
            if (inbox instanceof UIDFolder uidFolder) {
                // 获取最新的邮件UID
                Message[] allMessages = inbox.getMessages();
                long uidValidity = uidFolder.getUIDValidity();
                if (allMessages.length > 0) {
                    // 获取最大UID
                    long maxUid = 0;
                    for (Message msg : allMessages) {
                        long uid = uidFolder.getUID(msg);
                        if (uid > maxUid) {
                            maxUid = uid;
                        }
                    }

                    // 如果有最后同步的UID，则只拉取新的邮件
                    if (lastSyncUid != null && lastSyncUid > 0) {
                        // 拉取大于lastSyncUid的邮件
                        if (maxUid > lastSyncUid) {
                            Message[] newMessages = uidFolder.getMessagesByUID(lastSyncUid + 1, maxUid);
                            for (Message message : newMessages) {
                                try {
                                    String uniqueId = MailIdUtil.getUniqueEmailId(message);
                                    if (uniqueId != null) {
                                        result.add(uniqueId);
                                    }
                                } catch (Exception e) {
                                    log.error("获取邮件Id失败", e);
                                }
                            }
                        }
                    } else {
                        // 如果没有最后同步的UID，拉取最近的5封邮件
                        int total = inbox.getMessageCount();
                        int start = Math.max(1, total - 4);
                        Message[] recentMessages = inbox.getMessages(start, total);
                        for (Message message : recentMessages) {
                            long uid = uidFolder.getUID(message);
                            if (uid > lastSyncUid) {
                                try {
                                    String uniqueId = MailIdUtil.getUniqueEmailId(message);
                                    if (uniqueId != null) {
                                        result.add(uniqueId);
                                    }
                                } catch (Exception e) {
                                    log.error("获取邮件Id失败", e);
                                }
                            }
                        }
                    }

                    // 更新最后同步的UID
                    if (maxUid > 0) {
                        updateUidConfig(config, maxUid, uidValidity);
                    }
                }
            } else {
                log.warn("Folder is not a UIDFolder, falling back to original method");
                // 降级到原来的实现方式
                Message[] messages = inbox.getMessages();
                for (Message message : messages) {
                    try {
                        String uniqueId = MailIdUtil.getUniqueEmailId(message);
                        if (uniqueId != null) {
                            result.add(uniqueId);
                        }
                    } catch (Exception e) {
                        log.error("获取邮件Id失败", e);
                    }
                }
            }

            inbox.close(false);
            return result;
        } catch (Exception e) {
            log.error("拉取邮件Id列表失败", e);
            return new ArrayList<>();
        }
    }


    private MailRaw parseMessage(Message message) throws Exception {

        MailRaw mail = new MailRaw();

        mail.setMessageId(getHeader(message, "Message-ID"));
        mail.setSubject(message.getSubject());
        mail.setSentDate(message.getSentDate());

        mail.setFrom(((InternetAddress) message.getFrom()[0]).getAddress());

        mail.setTo(Arrays.stream(message.getRecipients(Message.RecipientType.TO))
                .map(addr -> ((InternetAddress) addr).getAddress())
                .toList()
        );

        extractContent(message, mail);

        return mail;
    }


    private String getHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }


    private void extractContent(Part part, MailRaw mail) throws Exception {

        if (part.isMimeType("text/plain") && mail.getTextBody() == null) {
            mail.setTextBody(part.getContent().toString());
            return;
        }

        if (part.isMimeType("text/html") && mail.getHtmlBody() == null) {
            mail.setHtmlBody(part.getContent().toString());
            return;
        }

        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                extractContent(multipart.getBodyPart(i), mail);
            }
        }

        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            mail.setHasAttachment(true);
        }
    }

    private void updateUidConfig(MailServerProperties.Imap config, long uid, long uidValidity) {

        String userName = config.getUserName();
        String host = config.getHost();
        Integer port = config.getPort();


        // 根据IMAP配置信息查找对应的邮箱账户并更新最后同步的UID
        MailAccount account = new MailAccount();
        account.setEmail(userName);
        account.setImapHost(host);
        account.setImapPort(port);

        // 查找已存在的账户记录
        LambdaQueryWrapper<MailAccount> wrapper = Wrappers.lambdaQuery(MailAccount.class)
                .eq(MailAccount::getEmail, userName)
                .eq(MailAccount::getImapHost, host)
                .eq(MailAccount::getImapPort, port);

        MailAccount existingAccount = mailAccountMapper.selectOne(wrapper);

        if (existingAccount != null) {
            // 更新已存在的账户
            existingAccount.setLastSyncUid(uid);
            existingAccount.setUidValidity(uidValidity);
            existingAccount.setLastSyncAt(LocalDateTime.now());
            mailAccountMapper.updateById(existingAccount);
        } else {
            // 创建新账户记录
            MailAccount newAccount = new MailAccount();
            newAccount.setEmail(config.getUserName());
            newAccount.setImapHost(config.getHost());
            newAccount.setImapPort(config.getPort());
            newAccount.setUsername(config.getUserName());
            newAccount.setLastSyncAt(LocalDateTime.now());
            newAccount.setLastSyncUid(uid);
            newAccount.setUidValidity(uidValidity);
            // 注意：出于安全考虑，这里不存储密码，实际生产中应该只存储账户标识信息
            mailAccountMapper.insert(newAccount);
        }
    }

    private Long getLastSyncUid(MailServerProperties.Imap config) {
        // 根据IMAP配置信息查找对应的邮箱账户的最后同步UID

        MailAccount account = mailAccountMapper.selectOne(
                Wrappers.lambdaQuery(MailAccount.class)
                        .eq(MailAccount::getEmail, config.getUserName())
                        .eq(MailAccount::getImapHost, config.getHost())
                        .eq(MailAccount::getImapPort, config.getPort())
        );

        if (account != null) {
            Long uid = account.getLastSyncUid();
            return uid == null ? 0L : uid;
        }
        return null;
    }


}
