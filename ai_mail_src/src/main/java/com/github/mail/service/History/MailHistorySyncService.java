package com.github.mail.service.History;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.Mail.domain.MailAccount;
import com.github.mail.repo.Mail.domain.MailFolderSyncState;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.mapper.MailFolderSyncStateMapper;
import com.github.mail.service.Fetcher.MailFetchService;
import com.github.mail.service.Fetcher.MailHistoryFetchResult;
import com.github.mail.service.Persistence.MailPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailHistorySyncService {

    private static final String SCOPE_HISTORY = "HISTORY";
    private static final String STATUS_SYNCING = "SYNCING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final MailFetchService mailFetchService;
    private final MailPersistenceService mailPersistenceService;
    private final MailFolderSyncStateMapper folderSyncStateMapper;
    private final MailConfig mailConfig;

    public MailAccount syncHistoryIfNeeded(MailConfig.Imap imapConfig) {
        MailAccount account = mailPersistenceService.ensureAccount(imapConfig);
        if (!mailConfig.getHistorySync().isEnabled() || Integer.valueOf(1).equals(account.getHistorySynced())) {
            return account;
        }

        mailPersistenceService.markHistorySyncStarted(account.getId());
        try {
            syncAccountHistory(account, imapConfig);
            mailPersistenceService.markHistorySyncCompleted(account.getId());
        } catch (Exception exception) {
            log.warn("历史邮件同步失败，继续处理当前新邮件: accountId={}", account.getId(), exception);
            mailPersistenceService.markHistorySyncFailed(account.getId(), exception.getMessage());
        }
        return account;
    }

    private void syncAccountHistory(MailAccount account, MailConfig.Imap imapConfig) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(mailConfig.getHistorySync().getLookbackDays());
        List<String> folders = mailFetchService.listSyncFolders(imapConfig);
        for (String folderName : folders) {
            syncFolder(account.getId(), imapConfig, folderName, cutoff);
        }
    }

    private void syncFolder(Long accountId, MailConfig.Imap imapConfig, String folderName, LocalDateTime cutoff) {
        MailFolderSyncState state = ensureFolderState(accountId, folderName);
        boolean hasMore = true;
        while (hasMore) {
            try {
                markFolderStarted(state);
                MailHistoryFetchResult result = mailFetchService.fetchHistoryMessages(
                        imapConfig,
                        folderName,
                        state.getUidValidity(),
                        state.getLastSyncedUid(),
                        cutoff,
                        mailConfig.getHistorySync().getBatchSize()
                );
                persistHistoryMessages(result.messages(), accountId);
                updateFolderCompleted(state, result);
                hasMore = result.messages().size() >= mailConfig.getHistorySync().getBatchSize();
            } catch (Exception exception) {
                markFolderFailed(state, exception);
                throw exception;
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void persistHistoryMessages(List<MailRaw> messages, Long accountId) {
        for (MailRaw message : messages) {
            mailPersistenceService.persistHistoryEmail(message, accountId);
        }
    }

    private MailFolderSyncState ensureFolderState(Long accountId, String folderName) {
        MailFolderSyncState state = folderSyncStateMapper.selectOne(
                Wrappers.lambdaQuery(MailFolderSyncState.class)
                        .eq(MailFolderSyncState::getMailAccountId, accountId)
                        .eq(MailFolderSyncState::getFolderName, folderName)
                        .eq(MailFolderSyncState::getSyncScope, SCOPE_HISTORY)
                        .last("limit 1")
        );
        if (state != null) {
            return state;
        }
        LocalDateTime now = LocalDateTime.now();
        state = new MailFolderSyncState();
        state.setMailAccountId(accountId);
        state.setFolderName(folderName);
        state.setSyncScope(SCOPE_HISTORY);
        state.setUidValidity(0L);
        state.setLastSyncedUid(0L);
        state.setSyncStatus(STATUS_SYNCING);
        state.setStartedAt(now);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        folderSyncStateMapper.insert(state);
        return state;
    }

    private void markFolderStarted(MailFolderSyncState state) {
        state.setSyncStatus(STATUS_SYNCING);
        state.setStartedAt(LocalDateTime.now());
        state.setLastError(null);
        state.setUpdatedAt(LocalDateTime.now());
        folderSyncStateMapper.updateById(state);
    }

    private void updateFolderCompleted(MailFolderSyncState state, MailHistoryFetchResult result) {
        boolean uidValidityChanged = !Long.valueOf(result.uidValidity()).equals(state.getUidValidity());
        state.setUidValidity(result.uidValidity());
        state.setLastSyncedUid(uidValidityChanged
                ? result.highestFetchedUid()
                : Math.max(state.getLastSyncedUid() == null ? 0L : state.getLastSyncedUid(), result.highestFetchedUid()));
        state.setSyncStatus(STATUS_COMPLETED);
        state.setCompletedAt(LocalDateTime.now());
        state.setLastError(null);
        state.setUpdatedAt(LocalDateTime.now());
        folderSyncStateMapper.updateById(state);
    }

    private void markFolderFailed(MailFolderSyncState state, Exception exception) {
        state.setSyncStatus(STATUS_FAILED);
        state.setLastError(exception.getMessage());
        state.setUpdatedAt(LocalDateTime.now());
        folderSyncStateMapper.updateById(state);
    }
}
