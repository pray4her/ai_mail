package com.github.mail.service.Persistence;

import com.github.mail.repo.Mail.domain.MailAttachment;
import com.github.mail.repo.Mail.domain.MailMessage;
import com.github.mail.repo.Mail.domain.MailProcessingRecord;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.dto.MailRawAttachment;
import com.github.mail.repo.Mail.mapper.MailAccountMapper;
import com.github.mail.repo.Mail.mapper.MailAttachmentMapper;
import com.github.mail.repo.Mail.mapper.MailMessageMapper;
import com.github.mail.repo.Mail.mapper.MailProcessingRecordMapper;
import com.github.mail.service.File.MinioStorageService;
import com.github.mail.utils.TikaDocumentParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailPersistenceServiceTest {

    @Test
    void persistEmail_storesAttachmentsInMinioAndDatabase() {
        MailMessageMapper mailMessageMapper = mock(MailMessageMapper.class);
        MailAttachmentMapper mailAttachmentMapper = mock(MailAttachmentMapper.class);
        MailProcessingRecordMapper processingRecordMapper = mock(MailProcessingRecordMapper.class);
        MailAccountMapper mailAccountMapper = mock(MailAccountMapper.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        TikaDocumentParser tikaDocumentParser = mock(TikaDocumentParser.class);

        when(mailMessageMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            MailMessage message = invocation.getArgument(0);
            message.setId(101L);
            return 1;
        }).when(mailMessageMapper).insert(any(MailMessage.class));
        doAnswer(invocation -> {
            MailAttachment attachment = invocation.getArgument(0);
            attachment.setId(201L);
            return 1;
        }).when(mailAttachmentMapper).insert(any(MailAttachment.class));
        when(tikaDocumentParser.extractText(any(), eq("resume.pdf"))).thenReturn("resume text");

        MailPersistenceService service = new MailPersistenceService(
                mailMessageMapper,
                mailAttachmentMapper,
                processingRecordMapper,
                mailAccountMapper,
                minioStorageService,
                tikaDocumentParser
        );

        MailRawAttachment attachment = new MailRawAttachment();
        attachment.setFilename("resume.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSize(8);
        attachment.setBytes("document".getBytes());
        attachment.setContentHash("hash-1");

        MailRaw mailRaw = new MailRaw();
        mailRaw.setMessageId("mid-1");
        mailRaw.setSubject("附件测试");
        mailRaw.setFrom("sender@example.com");
        mailRaw.setTextBody("body");
        mailRaw.setHasAttachment(true);
        mailRaw.setAttachmentCount(1);
        mailRaw.setAttachments(List.of(attachment));

        MailPersistenceService.PersistenceResult result = service.persistEmail(mailRaw, 1L);

        assertTrue(result.isSuccess());
        ArgumentCaptor<byte[]> fileBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(minioStorageService).uploadFile(
                eq("mail-attachments/101/hash-1-resume.pdf"),
                fileBytesCaptor.capture(),
                eq("application/pdf")
        );
        assertTrue(Arrays.equals("document".getBytes(), fileBytesCaptor.getValue()));
        ArgumentCaptor<MailAttachment> attachmentCaptor = ArgumentCaptor.forClass(MailAttachment.class);
        verify(mailAttachmentMapper).insert(attachmentCaptor.capture());
        assertEquals("mail-attachments/101/hash-1-resume.pdf", attachmentCaptor.getValue().getStoragePath());
        verify(processingRecordMapper).insert(any(MailProcessingRecord.class));
    }
}
