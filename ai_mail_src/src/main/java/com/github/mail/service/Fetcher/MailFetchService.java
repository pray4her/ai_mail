package com.github.mail.service.Fetcher;

import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.Mail.dto.MailRaw;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Aster
 * @date 2025/12/24
 */
public interface MailFetchService {

    /**
     * 拉取 INBOX 中的邮件给AI回复
     * @return MailRaw 经过处理的邮件信息
     */
     List<MailRaw> fetchToAiReply(MailConfig.Imap imapConfig);


    /**
     * 获取 INBOX 中的邮件ID
     * @return String 邮件ID
     */
     List<String> fetchInboxIds(MailConfig.Imap imapConfig);

     List<String> listSyncFolders(MailConfig.Imap imapConfig);

     MailHistoryFetchResult fetchHistoryMessages(MailConfig.Imap imapConfig,
                                                 String folderName,
                                                 Long expectedUidValidity,
                                                 Long lastSyncedUid,
                                                 LocalDateTime cutoff,
                                                 int limit);
}
