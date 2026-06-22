package com.github.mail.service.Fetcher.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.mail.model.config.MailConfig;
import com.github.mail.model.config.Properties.MailServerProperties;
import com.github.mail.repo.Mail.domain.MailAccount;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.mapper.MailAccountMapper;
import com.github.mail.service.Fetcher.MailFetchService;
import com.github.mail.service.Fetcher.MailHistoryFetchResult;
import com.github.mail.service.Fetcher.MailMessageParser;
import com.github.mail.utils.MailConnectUtil;
import com.github.mail.utils.MailIdUtil;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SentDateTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailFetchServiceImpl implements MailFetchService {

    private static final int LIVE_FETCH_LIMIT = 50;
    private static final Set<String> SKIPPED_HISTORY_FOLDERS = Set.of(
            "draft", "drafts", "trash", "deleted", "deleted messages", "spam", "junk"
    );

    private final MailAccountMapper mailAccountMapper;
    private final MailMessageParser mailMessageParser;

    @Override
    public List<MailRaw> fetchToAiReply(MailConfig.Imap imapConfig) {
        MailServerProperties.Imap config = MailServerProperties.fromMailConfig(imapConfig);
        List<MailRaw> result = new ArrayList<>();
        try (Store store = MailConnectUtil.connect(config)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            Long lastSyncUid = getLastSyncUid(config);
            if (inbox instanceof UIDFolder uidFolder) {
                fetchLiveByUid(config, result, inbox, uidFolder, lastSyncUid);
            } else {
                fetchRecentWithoutUid(result, inbox);
            }
            inbox.close(false);
        } catch (Exception e) {
            log.error("拉取邮件失败", e);
        }
        return result;
    }

    @Override
    public List<String> fetchInboxIds(MailConfig.Imap imapConfig) {
        return fetchToAiReply(imapConfig).stream()
                .map(MailRaw::getMessageId)
                .filter(messageId -> messageId != null && !messageId.isBlank())
                .toList();
    }

    @Override
    public List<String> listSyncFolders(MailConfig.Imap imapConfig) {
        MailServerProperties.Imap config = MailServerProperties.fromMailConfig(imapConfig);
        try (Store store = MailConnectUtil.connect(config)) {
            Folder[] folders = store.getDefaultFolder().list("*");
            List<String> names = new ArrayList<>();
            for (Folder folder : folders) {
                if (holdsMessages(folder) && !isSkippedHistoryFolder(folder.getFullName())) {
                    names.add(folder.getFullName());
                }
            }
            return names.stream().distinct().toList();
        } catch (Exception e) {
            throw new IllegalStateException("列出 IMAP 文件夹失败: " + config.getUserName(), e);
        }
    }

    @Override
    public MailHistoryFetchResult fetchHistoryMessages(MailConfig.Imap imapConfig,
                                                       String folderName,
                                                       Long expectedUidValidity,
                                                       Long lastSyncedUid,
                                                       LocalDateTime cutoff,
                                                       int limit) {
        MailServerProperties.Imap config = MailServerProperties.fromMailConfig(imapConfig);
        try (Store store = MailConnectUtil.connect(config)) {
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);
            if (!(folder instanceof UIDFolder uidFolder)) {
                folder.close(false);
                return new MailHistoryFetchResult(folderName, -1, lastSyncedUid == null ? 0 : lastSyncedUid, List.of());
            }
            long uidValidity = uidFolder.getUIDValidity();
            long effectiveLastUid = expectedUidValidity != null && expectedUidValidity == uidValidity
                    ? nullToZero(lastSyncedUid)
                    : 0L;
            Message[] candidates = folder.search(recentMessages(cutoff));
            prefetch(folder, candidates);
            List<MessageWithUid> ordered = messagesAfterUid(uidFolder, candidates, effectiveLastUid);
            List<MailRaw> messages = new ArrayList<>();
            long highestUid = effectiveLastUid;
            for (MessageWithUid item : ordered) {
                if (messages.size() >= limit) {
                    break;
                }
                highestUid = Math.max(highestUid, item.uid());
                messages.add(mailMessageParser.parse(item.message(), folderName, item.uid(), uidValidity, true));
            }
            folder.close(false);
            return new MailHistoryFetchResult(folderName, uidValidity, highestUid, messages);
        } catch (Exception e) {
            throw new IllegalStateException("同步历史邮件失败: " + folderName, e);
        }
    }

    private void fetchLiveByUid(MailServerProperties.Imap config,
                                List<MailRaw> result,
                                Folder inbox,
                                UIDFolder uidFolder,
                                Long lastSyncUid) throws Exception {
        long uidValidity = uidFolder.getUIDValidity();
        if (lastSyncUid == null || lastSyncUid <= 0) {
            updateInitialUid(config, inbox, uidFolder, uidValidity);
            return;
        }
        Message[] newMessages = uidFolder.getMessagesByUID(lastSyncUid + 1, UIDFolder.LASTUID);
        long maxUid = lastSyncUid;
        for (int i = 0; i < Math.min(newMessages.length, LIVE_FETCH_LIMIT); i++) {
            long uid = uidFolder.getUID(newMessages[i]);
            if (uid <= lastSyncUid) {
                continue;
            }
            maxUid = Math.max(maxUid, uid);
            result.add(mailMessageParser.parse(newMessages[i], "INBOX", uid, uidValidity, false));
        }
        if (maxUid > lastSyncUid) {
            updateUidConfig(config, maxUid, uidValidity);
        }
    }

    private void updateInitialUid(MailServerProperties.Imap config,
                                  Folder inbox,
                                  UIDFolder uidFolder,
                                  long uidValidity) throws MessagingException {
        int total = inbox.getMessageCount();
        if (total == 0) {
            return;
        }
        int start = Math.max(1, total - 9);
        Message[] recentMessages = inbox.getMessages(start, total);
        long maxUid = 0;
        for (Message message : recentMessages) {
            maxUid = Math.max(maxUid, uidFolder.getUID(message));
        }
        if (maxUid > 0) {
            updateUidConfig(config, maxUid, uidValidity);
        }
    }

    private void fetchRecentWithoutUid(List<MailRaw> result, Folder inbox) throws Exception {
        int total = inbox.getMessageCount();
        int start = Math.max(1, total - 4);
        for (Message message : inbox.getMessages(start, total)) {
            result.add(mailMessageParser.parse(message, "INBOX", null, null, false));
        }
    }

    private boolean holdsMessages(Folder folder) throws MessagingException {
        return (folder.getType() & Folder.HOLDS_MESSAGES) != 0;
    }

    private boolean isSkippedHistoryFolder(String folderName) {
        String normalized = folderName == null ? "" : folderName.toLowerCase(Locale.ROOT);
        return SKIPPED_HISTORY_FOLDERS.stream().anyMatch(normalized::contains);
    }

    private SearchTerm recentMessages(LocalDateTime cutoff) {
        Date cutoffDate = Date.from(cutoff.atZone(ZoneId.systemDefault()).toInstant());
        return new OrTerm(
                new ReceivedDateTerm(ComparisonTerm.GE, cutoffDate),
                new SentDateTerm(ComparisonTerm.GE, cutoffDate)
        );
    }

    private void prefetch(Folder folder, Message[] messages) throws MessagingException {
        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(FetchProfile.Item.ENVELOPE);
        fetchProfile.add(FetchProfile.Item.FLAGS);
        folder.fetch(messages, fetchProfile);
    }

    private List<MessageWithUid> messagesAfterUid(UIDFolder uidFolder, Message[] messages, long lastUid)
            throws MessagingException {
        List<MessageWithUid> results = new ArrayList<>();
        Set<Long> seenUids = new HashSet<>();
        for (Message message : messages) {
            long uid = uidFolder.getUID(message);
            if (uid > lastUid && seenUids.add(uid)) {
                results.add(new MessageWithUid(message, uid));
            }
        }
        results.sort(Comparator.comparingLong(MessageWithUid::uid));
        return results;
    }

    private Long getLastSyncUid(MailServerProperties.Imap config) {
        MailAccount account = mailAccountMapper.selectOne(
                Wrappers.lambdaQuery(MailAccount.class)
                        .eq(MailAccount::getEmail, config.getUserName())
                        .eq(MailAccount::getImapHost, config.getHost())
                        .eq(MailAccount::getImapPort, config.getPort())
        );
        return account == null || account.getLastSyncUid() == null ? null : account.getLastSyncUid();
    }

    private void updateUidConfig(MailServerProperties.Imap config, long uid, long uidValidity) {
        LambdaQueryWrapper<MailAccount> wrapper = Wrappers.lambdaQuery(MailAccount.class)
                .eq(MailAccount::getEmail, config.getUserName())
                .eq(MailAccount::getImapHost, config.getHost())
                .eq(MailAccount::getImapPort, config.getPort());
        MailAccount account = mailAccountMapper.selectOne(wrapper);
        if (account == null) {
            account = new MailAccount();
            account.setEmail(config.getUserName());
            account.setImapHost(config.getHost());
            account.setImapPort(config.getPort());
            account.setUsername(config.getUserName());
            account.setUseSsl(config.isSsl() ? 1 : 0);
            account.setHistorySynced(0);
            account.setIsDeleted(0);
            account.setCreatedAt(LocalDateTime.now());
            account.setPassword("0");
            account.setLastSyncUid(uid);
            account.setUidValidity(uidValidity);
            account.setLastSyncAt(LocalDateTime.now());
            mailAccountMapper.insert(account);
            return;
        }
        account.setLastSyncUid(uid);
        account.setUidValidity(uidValidity);
        account.setLastSyncAt(LocalDateTime.now());
        mailAccountMapper.updateById(account);
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private record MessageWithUid(Message message, long uid) {
    }
}
