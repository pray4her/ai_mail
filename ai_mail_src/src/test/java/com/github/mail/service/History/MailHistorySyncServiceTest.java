package com.github.mail.service.History;

import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.Mail.domain.MailAccount;
import com.github.mail.repo.Mail.domain.MailFolderSyncState;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.mapper.MailFolderSyncStateMapper;
import com.github.mail.service.Fetcher.MailFetchService;
import com.github.mail.service.Fetcher.MailHistoryFetchResult;
import com.github.mail.service.Persistence.MailPersistenceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailHistorySyncServiceTest {

    @Test
    void syncHistoryIfNeeded_usesFolderCursorAndMarksCompleted() {
        MailFetchService mailFetchService = mock(MailFetchService.class);
        MailPersistenceService mailPersistenceService = mock(MailPersistenceService.class);
        MailFolderSyncStateMapper folderSyncStateMapper = mock(MailFolderSyncStateMapper.class);
        MailConfig mailConfig = new MailConfig();
        mailConfig.getHistorySync().setBatchSize(50);

        MailConfig.Imap imap = new MailConfig.Imap();
        imap.setUsername("user@qq.com");
        MailAccount account = new MailAccount();
        account.setId(1L);
        account.setEmail("user@qq.com");
        account.setHistorySynced(0);
        when(mailPersistenceService.ensureAccount(imap)).thenReturn(account);

        MailFolderSyncState state = new MailFolderSyncState();
        state.setId(10L);
        state.setMailAccountId(1L);
        state.setFolderName("INBOX");
        state.setSyncScope("HISTORY");
        state.setUidValidity(88L);
        state.setLastSyncedUid(12L);
        when(folderSyncStateMapper.selectOne(any())).thenReturn(state);

        MailRaw historyMail = new MailRaw();
        historyMail.setMessageId("mid-history");
        when(mailFetchService.listSyncFolders(imap)).thenReturn(List.of("INBOX"));
        when(mailFetchService.fetchHistoryMessages(eq(imap), eq("INBOX"), eq(88L), eq(12L), any(LocalDateTime.class), eq(50)))
                .thenReturn(new MailHistoryFetchResult("INBOX", 88L, 18L, List.of(historyMail)));

        MailHistorySyncService service = new MailHistorySyncService(
                mailFetchService,
                mailPersistenceService,
                folderSyncStateMapper,
                mailConfig
        );

        service.syncHistoryIfNeeded(imap);

        verify(mailPersistenceService).markHistorySyncStarted(1L);
        verify(mailPersistenceService).persistHistoryEmail(historyMail, 1L);
        verify(mailPersistenceService).markHistorySyncCompleted(1L);
        ArgumentCaptor<MailFolderSyncState> stateCaptor = ArgumentCaptor.forClass(MailFolderSyncState.class);
        verify(folderSyncStateMapper, org.mockito.Mockito.atLeastOnce()).updateById(stateCaptor.capture());
        MailFolderSyncState latestState = stateCaptor.getAllValues().get(stateCaptor.getAllValues().size() - 1);
        assertEquals(18L, latestState.getLastSyncedUid());
        assertEquals("COMPLETED", latestState.getSyncStatus());
    }

    @Test
    void syncHistoryIfNeeded_createsFolderStateForFirstSync() {
        MailFetchService mailFetchService = mock(MailFetchService.class);
        MailPersistenceService mailPersistenceService = mock(MailPersistenceService.class);
        MailFolderSyncStateMapper folderSyncStateMapper = mock(MailFolderSyncStateMapper.class);
        MailConfig mailConfig = new MailConfig();

        MailConfig.Imap imap = new MailConfig.Imap();
        MailAccount account = new MailAccount();
        account.setId(2L);
        account.setHistorySynced(0);
        when(mailPersistenceService.ensureAccount(imap)).thenReturn(account);
        when(mailFetchService.listSyncFolders(imap)).thenReturn(List.of("Sent Messages"));
        when(folderSyncStateMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            MailFolderSyncState state = invocation.getArgument(0);
            state.setId(20L);
            return 1;
        }).when(folderSyncStateMapper).insert(any(MailFolderSyncState.class));
        when(mailFetchService.fetchHistoryMessages(eq(imap), eq("Sent Messages"), eq(0L), eq(0L), any(LocalDateTime.class), eq(50)))
                .thenReturn(new MailHistoryFetchResult("Sent Messages", 99L, 0L, List.of()));

        MailHistorySyncService service = new MailHistorySyncService(
                mailFetchService,
                mailPersistenceService,
                folderSyncStateMapper,
                mailConfig
        );

        service.syncHistoryIfNeeded(imap);

        ArgumentCaptor<MailFolderSyncState> insertCaptor = ArgumentCaptor.forClass(MailFolderSyncState.class);
        verify(folderSyncStateMapper).insert(insertCaptor.capture());
        assertEquals("Sent Messages", insertCaptor.getValue().getFolderName());
        assertEquals("HISTORY", insertCaptor.getValue().getSyncScope());
        verify(mailPersistenceService).markHistorySyncCompleted(2L);
    }

    @Test
    void syncHistoryIfNeeded_resetsCursorWhenUidValidityChanges() {
        MailFetchService mailFetchService = mock(MailFetchService.class);
        MailPersistenceService mailPersistenceService = mock(MailPersistenceService.class);
        MailFolderSyncStateMapper folderSyncStateMapper = mock(MailFolderSyncStateMapper.class);
        MailConfig mailConfig = new MailConfig();

        MailConfig.Imap imap = new MailConfig.Imap();
        MailAccount account = new MailAccount();
        account.setId(3L);
        account.setHistorySynced(0);
        when(mailPersistenceService.ensureAccount(imap)).thenReturn(account);
        when(mailFetchService.listSyncFolders(imap)).thenReturn(List.of("INBOX"));

        MailFolderSyncState state = new MailFolderSyncState();
        state.setId(30L);
        state.setMailAccountId(3L);
        state.setFolderName("INBOX");
        state.setSyncScope("HISTORY");
        state.setUidValidity(88L);
        state.setLastSyncedUid(500L);
        when(folderSyncStateMapper.selectOne(any())).thenReturn(state);
        when(mailFetchService.fetchHistoryMessages(eq(imap), eq("INBOX"), eq(88L), eq(500L), any(LocalDateTime.class), eq(50)))
                .thenReturn(new MailHistoryFetchResult("INBOX", 99L, 12L, List.of()));

        MailHistorySyncService service = new MailHistorySyncService(
                mailFetchService,
                mailPersistenceService,
                folderSyncStateMapper,
                mailConfig
        );

        service.syncHistoryIfNeeded(imap);

        ArgumentCaptor<MailFolderSyncState> stateCaptor = ArgumentCaptor.forClass(MailFolderSyncState.class);
        verify(folderSyncStateMapper, org.mockito.Mockito.atLeastOnce()).updateById(stateCaptor.capture());
        MailFolderSyncState latestState = stateCaptor.getAllValues().get(stateCaptor.getAllValues().size() - 1);
        assertEquals(99L, latestState.getUidValidity());
        assertEquals(12L, latestState.getLastSyncedUid());
    }
}
