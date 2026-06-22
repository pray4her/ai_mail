package com.github.mail.service.Schedule;

import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.repo.Mail.domain.MailAccount;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.service.Fetcher.MailFetchService;
import com.github.mail.service.History.MailHistoryContextService;
import com.github.mail.service.History.MailHistorySyncService;
import com.github.mail.service.KnowledgeBase.RagService;
import com.github.mail.service.MailOperation.MailSendService;
import com.github.mail.service.Persistence.MailPersistenceService;
import com.github.mail.service.ai.AiGenerationRequest;
import com.github.mail.service.ai.AiGenerationResult;
import com.github.mail.service.ai.AiGenerationService;
import com.github.mail.utils.TikaDocumentParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class MailAutoReplySchedulerTest {

    @Test
    void autoGenerateReply_usesAiGenerationServiceAndSavesDraft() {
        MailFetchService mailFetchService = mock(MailFetchService.class);
        RagService ragService = mock(RagService.class);
        AiGenerationService aiGenerationService = mock(AiGenerationService.class);
        MailSendService mailSendService = mock(MailSendService.class);
        MailPersistenceService mailPersistenceService = mock(MailPersistenceService.class);
        MailHistorySyncService mailHistorySyncService = mock(MailHistorySyncService.class);
        MailHistoryContextService mailHistoryContextService = mock(MailHistoryContextService.class);
        TikaDocumentParser tikaDocumentParser = mock(TikaDocumentParser.class);

        MailConfig mailConfig = new MailConfig();
        MailConfig.Imap imap = new MailConfig.Imap();
        imap.setUsername("user@example.com");
        mailConfig.setImapList(List.of(imap));
        mailConfig.getAutoReply().getInterval().setLevel1(90);
        mailConfig.getAutoReply().getInterval().setLevel2(300);
        mailConfig.getAutoReply().getInterval().setLevel3(600);
        mailConfig.getAutoReply().getThreshold().setEmptyCount1(3);
        mailConfig.getAutoReply().getThreshold().setEmptyCount2(6);
        mailConfig.getAutoReply().setDraftFolder("AI_reply");
        mailConfig.getRag().setTopK(5);
        mailConfig.getRag().setMinScore(0.3);

        MailRaw mailRaw = new MailRaw();
        mailRaw.setMessageId("mid-1");
        mailRaw.setSubject("咨询合作");
        mailRaw.setFrom("sender@example.com");
        mailRaw.setTextBody("正文");

        when(mailFetchService.fetchToAiReply(imap)).thenReturn(List.of(mailRaw));
        when(tikaDocumentParser.getEffectiveText("正文", null)).thenReturn("请介绍合作方式");
        when(ragService.batchRetrieveRagChunks(List.of("主题: 咨询合作\n正文: 请介绍合作方式"), 5, 0.3))
                .thenReturn(List.of(List.of(new RagChunk("合作知识", 0.9, "1"))));
        MailAccount account = new MailAccount();
        account.setId(1L);
        account.setEmail("user@example.com");
        when(mailHistorySyncService.syncHistoryIfNeeded(imap)).thenReturn(account);
        when(mailPersistenceService.persistEmail(mailRaw, 1L))
                .thenReturn(MailPersistenceService.PersistenceResult.success(11L));
        when(mailPersistenceService.loadGenerationAttachments(11L)).thenReturn(List.of());
        when(mailHistoryContextService.buildContext(1L, "user@example.com", "sender@example.com", 11L))
                .thenReturn("历史上下文");
        when(aiGenerationService.generate(any(AiGenerationRequest.class)))
                .thenReturn(new AiGenerationResult(
                        "Subject: Re: 咨询合作\r\n\r\n这是 AI 回复",
                        "default",
                        "chat-model",
                        null,
                        null,
                        null,
                        null,
                        "trace-1"
                ));

        MailAutoReplyScheduler scheduler = new MailAutoReplyScheduler(
                mailFetchService,
                ragService,
                aiGenerationService,
                mailSendService,
                mailPersistenceService,
                mailHistorySyncService,
                mailHistoryContextService,
                tikaDocumentParser,
                mailConfig
        );

        scheduler.autoGenerateReply();

        InOrder inOrder = inOrder(mailHistorySyncService, mailPersistenceService, aiGenerationService);
        inOrder.verify(mailHistorySyncService).syncHistoryIfNeeded(imap);
        inOrder.verify(mailPersistenceService).persistEmail(mailRaw, 1L);
        ArgumentCaptor<AiGenerationRequest> requestCaptor = ArgumentCaptor.forClass(AiGenerationRequest.class);
        inOrder.verify(aiGenerationService).generate(requestCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("历史上下文", requestCaptor.getValue().historyContext());
        verify(mailSendService).saveDraftToFolder(
                eq("sender@example.com"),
                eq("咨询合作"),
                eq("这是 AI 回复"),
                eq("AI_reply"),
                eq(imap)
        );
        verify(mailPersistenceService).markReplyDraftSaved(11L, "AI_reply", "这是 AI 回复");
    }
}
