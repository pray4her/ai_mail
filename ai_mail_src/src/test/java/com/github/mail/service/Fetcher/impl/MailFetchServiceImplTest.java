package com.github.mail.service.Fetcher.impl;

import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.dto.MailRawAttachment;
import com.github.mail.service.Fetcher.MailMessageParser;
import com.github.mail.service.Fetcher.QqLargeAttachmentDetector;
import jakarta.activation.DataHandler;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
class MailFetchServiceImplTest {

    @Test
    void parseMessage_extractsBodyAndAttachmentMetadata() throws Exception {
        MailMessageParser parser = new MailMessageParser(new QqLargeAttachmentDetector());
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setHeader("Message-ID", "<mid-1>");
        message.setSubject("附件测试", "UTF-8");
        message.setFrom(new InternetAddress("sender@example.com"));
        message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress("receiver@example.com"));

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("plain body", "UTF-8");

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent("<p>html body</p>", "text/html; charset=UTF-8");

        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(textPart);
        alternative.addBodyPart(htmlPart);
        MimeBodyPart alternativeWrapper = new MimeBodyPart();
        alternativeWrapper.setContent(alternative);

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setFileName("resume.pdf");
        attachmentPart.setDisposition(MimeBodyPart.ATTACHMENT);
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource("pdf-bytes".getBytes(), "application/pdf")));

        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(alternativeWrapper);
        mixed.addBodyPart(attachmentPart);
        message.setContent(mixed);
        message.saveChanges();

        MailRaw mailRaw = parser.parse(message, "INBOX", null, null, false);

        assertNotNull(mailRaw.getMessageId());
        assertTrue(mailRaw.getMessageId().startsWith("<"));
        assertEquals("plain body", mailRaw.getTextBody());
        assertEquals("<p>html body</p>", mailRaw.getHtmlBody());
        assertTrue(mailRaw.isHasAttachment());
        assertEquals(1, mailRaw.getAttachmentCount());
        assertNotNull(mailRaw.getAttachments());
        MailRawAttachment attachment = mailRaw.getAttachments().get(0);
        assertEquals("resume.pdf", attachment.getFilename());
        assertEquals("application/pdf", attachment.getContentType());
        assertTrue(attachment.getSize() > 0);
        assertNotNull(attachment.getContentHash());
        assertFalse(attachment.getContentHash().isBlank());
    }

    @Test
    void parseMessage_infersSpecificMimeTypeWhenAttachmentIsGenericOctetStream() throws Exception {
        MailMessageParser parser = new MailMessageParser(new QqLargeAttachmentDetector());
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setHeader("Message-ID", "<mid-2>");
        message.setSubject("附件测试2", "UTF-8");
        message.setFrom(new InternetAddress("sender@example.com"));
        message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress("receiver@example.com"));

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("plain body", "UTF-8");

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setFileName("resume.docx");
        attachmentPart.setDisposition(MimeBodyPart.ATTACHMENT);
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource("docx-bytes".getBytes(), "APPLICATION/OCTET-STREAM")));

        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(textPart);
        mixed.addBodyPart(attachmentPart);
        message.setContent(mixed);
        message.saveChanges();

        MailRaw mailRaw = parser.parse(message, "INBOX", null, null, false);

        assertEquals(1, mailRaw.getAttachmentCount());
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                mailRaw.getAttachments().get(0).getContentType()
        );
    }

    @Test
    void parseMessage_decodesMimeEncodedFilenameBeforeInferringMimeType() throws Exception {
        MailMessageParser parser = new MailMessageParser(new QqLargeAttachmentDetector());
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setHeader("Message-ID", "<mid-3>");
        message.setSubject("附件测试3", "UTF-8");
        message.setFrom(new InternetAddress("sender@example.com"));
        message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress("receiver@example.com"));

        MimeBodyPart attachmentPart = new MimeBodyPart();
        String encodedFilename = MimeUtility.encodeText("resume.pdf", "UTF-8", "B");
        attachmentPart.setFileName(encodedFilename);
        attachmentPart.setDisposition(MimeBodyPart.ATTACHMENT);
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource("pdf-bytes".getBytes(), "application/octet-stream")));

        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(attachmentPart);
        message.setContent(mixed);
        message.saveChanges();

        MailRaw mailRaw = parser.parse(message, "INBOX", null, null, false);

        assertEquals(1, mailRaw.getAttachmentCount());
        assertEquals("resume.pdf", mailRaw.getAttachments().get(0).getFilename());
        assertEquals("application/pdf", mailRaw.getAttachments().get(0).getContentType());
    }

    @Test
    void parseMessage_detectsQqLargeAttachmentLinksAndKeepsRawMime() throws Exception {
        MailMessageParser parser = new MailMessageParser(new QqLargeAttachmentDetector());
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setHeader("Message-ID", "<mid-4>");
        message.setSubject("超大附件", "UTF-8");
        message.setFrom(new InternetAddress("sender@qq.com"));
        message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress("receiver@qq.com"));
        message.setText("请下载 https://mail.qq.com/cgi-bin/ftnExs_download?file=large.zip&k=abc", "UTF-8");
        message.saveChanges();

        MailRaw mailRaw = parser.parse(message, "INBOX", 15L, 99L, true);

        assertTrue(mailRaw.isHistory());
        assertEquals(15L, mailRaw.getImapUid());
        assertEquals(99L, mailRaw.getFolderUidValidity());
        assertEquals(1, mailRaw.getAttachmentCount());
        assertEquals("REMOTE_LINK", mailRaw.getAttachments().get(0).getAttachmentKind());
        assertEquals("large.zip", mailRaw.getAttachments().get(0).getFilename());
        assertNotNull(mailRaw.getRawMimeBytes());
        assertTrue(mailRaw.getRawMimeBytes().length > 0);
    }
}
