package com.github.mail.service.Schedule;

import com.github.mail.repo.Mail.mapper.MailAccountMapper;
import com.github.mail.service.Fetcher.MailFetchService;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleResult;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleService;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleStatus;
import com.github.mail.service.Persistence.MailPersistenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessingSchedulerTest {

    @Test
    void cleanupFailedDocuments_delegatesToLifecycleCleanup() {
        MailFetchService fetchService = mock(MailFetchService.class);
        MailPersistenceService persistenceService = mock(MailPersistenceService.class);
        KbDocumentLifecycleService lifecycleService = mock(KbDocumentLifecycleService.class);
        MailAccountMapper mailAccountMapper = mock(MailAccountMapper.class);
        ProcessingScheduler scheduler = new ProcessingScheduler(
                fetchService,
                persistenceService,
                lifecycleService,
                mailAccountMapper
        );

        when(lifecycleService.cleanupFailedDocuments()).thenReturn(List.of(
                KbDocumentLifecycleResult.success(
                        60L,
                        KbDocumentLifecycleStatus.FAILED,
                        0,
                        0,
                        "知识库文档已删除"
                ),
                KbDocumentLifecycleResult.failure(
                        61L,
                        KbDocumentLifecycleStatus.FAILED,
                        "对象存储清理失败"
                )
        ));

        scheduler.cleanupFailedDocuments();

        verify(lifecycleService).cleanupFailedDocuments();
    }
}
