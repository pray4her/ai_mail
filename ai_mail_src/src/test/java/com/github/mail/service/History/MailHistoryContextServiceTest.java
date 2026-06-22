package com.github.mail.service.History;

import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.Mail.domain.MailAttachment;
import com.github.mail.repo.Mail.domain.MailMessage;
import com.github.mail.repo.Mail.mapper.MailAttachmentMapper;
import com.github.mail.repo.Mail.mapper.MailMessageMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailHistoryContextServiceTest {

    @Test
    void buildContext_includesBidirectionalHistoryAndExcludesCurrentMessage() {
        MailMessageMapper mailMessageMapper = mock(MailMessageMapper.class);
        MailAttachmentMapper mailAttachmentMapper = mock(MailAttachmentMapper.class);
        MailConfig mailConfig = new MailConfig();
        mailConfig.getHistorySync().setMaxContextMessages(20);
        mailConfig.getHistorySync().setMaxContextChars(12000);

        MailMessage inbound = message(1L, "customer@qq.com", List.of("user@qq.com"), "INBOUND", "客户旧邮件", "之前的问题");
        MailMessage outbound = message(2L, "user@qq.com", List.of("customer@qq.com"), "OUTBOUND", "历史回复", "之前的答复");
        MailMessage current = message(3L, "customer@qq.com", List.of("user@qq.com"), "INBOUND", "当前邮件", "不要重复");
        MailMessage unrelated = message(4L, "other@qq.com", List.of("user@qq.com"), "INBOUND", "无关邮件", "不应出现");

        when(mailMessageMapper.selectList(any())).thenReturn(List.of(inbound, outbound, current, unrelated));
        MailAttachment attachment = new MailAttachment();
        attachment.setFilename("quote.pdf");
        attachment.setContentType("application/pdf");
        when(mailAttachmentMapper.selectList(any())).thenReturn(List.of(attachment));

        MailHistoryContextService service = new MailHistoryContextService(
                mailMessageMapper,
                mailAttachmentMapper,
                mailConfig
        );

        String context = service.buildContext(1L, "user@qq.com", "customer@qq.com", 3L);

        assertTrue(context.contains("客户旧邮件"));
        assertTrue(context.contains("历史回复"));
        assertTrue(context.contains("quote.pdf"));
        assertFalse(context.contains("当前邮件"));
        assertFalse(context.contains("无关邮件"));
    }

    @Test
    void buildContext_limitsMessageCountAndCharacters() {
        MailMessageMapper mailMessageMapper = mock(MailMessageMapper.class);
        MailAttachmentMapper mailAttachmentMapper = mock(MailAttachmentMapper.class);
        MailConfig mailConfig = new MailConfig();
        mailConfig.getHistorySync().setMaxContextMessages(1);
        mailConfig.getHistorySync().setMaxContextChars(80);

        MailMessage first = message(1L, "customer@qq.com", List.of("user@qq.com"), "INBOUND", "第一封", "a".repeat(200));
        MailMessage second = message(2L, "customer@qq.com", List.of("user@qq.com"), "INBOUND", "第二封", "b".repeat(200));
        when(mailMessageMapper.selectList(any())).thenReturn(List.of(first, second));
        when(mailAttachmentMapper.selectList(any())).thenReturn(List.of());

        MailHistoryContextService service = new MailHistoryContextService(
                mailMessageMapper,
                mailAttachmentMapper,
                mailConfig
        );

        String context = service.buildContext(1L, "user@qq.com", "customer@qq.com", null);

        assertTrue(context.length() <= 80);
        assertFalse(context.contains("第二封"));
    }

    private MailMessage message(Long id, String from, List<String> to, String direction, String subject, String body) {
        MailMessage message = new MailMessage();
        message.setId(id);
        message.setMailAccountId(1L);
        message.setFromEmail(from);
        message.setToEmails(to);
        message.setDirection(direction);
        message.setSubject(subject);
        message.setBodyText(body);
        message.setSentAt(LocalDateTime.now().minusDays(id));
        message.setReceivedAt(LocalDateTime.now().minusDays(id));
        message.setIsDeleted(0);
        return message;
    }
}
