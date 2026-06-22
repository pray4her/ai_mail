package com.github.mail.service.Fetcher;

import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.dto.MailRawAttachment;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailMessageParser {

    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp")
    );

    private final QqLargeAttachmentDetector qqLargeAttachmentDetector;

    public MailRaw parse(Message message,
                         String folderName,
                         Long imapUid,
                         Long folderUidValidity,
                         boolean history) throws Exception {
        MailRaw mail = new MailRaw();
        mail.setAttachments(new ArrayList<>());
        mail.setFolderName(folderName);
        mail.setImapUid(imapUid);
        mail.setFolderUidValidity(folderUidValidity);
        mail.setHistory(history);
        mail.setMessageId(resolveMessageId(message));
        mail.setSubject(message.getSubject());
        mail.setSentDate(message.getSentDate());
        mail.setFrom(resolveFirstAddress(message.getFrom()));
        mail.setTo(resolveRecipients(message, Message.RecipientType.TO));
        mail.setCc(resolveRecipients(message, Message.RecipientType.CC));
        mail.setBcc(resolveRecipients(message, Message.RecipientType.BCC));
        mail.setInReplyTo(getHeader(message, "In-Reply-To"));
        mail.setMailReferences(getHeader(message, "References"));

        extractContent(message, mail);
        addRemoteAttachments(mail, message);
        mail.setAttachmentCount(mail.getAttachments().size());
        mail.setHasAttachment(!mail.getAttachments().isEmpty());
        return mail;
    }

    private String resolveMessageId(Message message) throws MessagingException {
        String messageId = getHeader(message, "Message-ID");
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        String subject = message.getSubject() == null ? "" : message.getSubject();
        String sentDate = message.getSentDate() == null ? "" : message.getSentDate().toString();
        return String.valueOf((subject + sentDate + message.getMessageNumber()).hashCode());
    }

    private String resolveFirstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        if (addresses[0] instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress();
        }
        return addresses[0].toString();
    }

    private List<String> resolveRecipients(Message message, Message.RecipientType type) throws MessagingException {
        Address[] recipients = message.getRecipients(type);
        if (recipients == null || recipients.length == 0) {
            return List.of();
        }
        return Arrays.stream(recipients)
                .map(this::toAddress)
                .toList();
    }

    private String toAddress(Address address) {
        if (address instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress();
        }
        return address.toString();
    }

    private String getHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private void extractContent(Part part, MailRaw mail) throws Exception {
        if (isAttachmentPart(part)) {
            mail.getAttachments().add(extractAttachment(part));
            return;
        }
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
            return;
        }
        if (part.isMimeType("message/rfc822") && part.getContent() instanceof Part nestedPart) {
            extractContent(nestedPart, mail);
        }
    }

    private boolean isAttachmentPart(Part part) throws MessagingException {
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition) || Part.INLINE.equalsIgnoreCase(disposition)) {
            return part.getFileName() != null;
        }
        return part.getFileName() != null && !part.isMimeType("text/plain") && !part.isMimeType("text/html");
    }

    private MailRawAttachment extractAttachment(Part part) throws Exception {
        byte[] bytes;
        try (var inputStream = part.getInputStream()) {
            bytes = inputStream.readAllBytes();
        }
        String filename = decodeMimeText(part.getFileName());
        MailRawAttachment attachment = new MailRawAttachment();
        attachment.setFilename(filename == null || filename.isBlank() ? UUID.randomUUID() + ".bin" : filename);
        attachment.setContentType(resolveContentType(part, filename));
        attachment.setSize(bytes.length);
        attachment.setBytes(bytes);
        attachment.setContentHash(sha256Hex(bytes));
        attachment.setAttachmentKind("MINIO");
        return attachment;
    }

    private String resolveContentType(Part part, String filename) throws MessagingException {
        String normalizedRawType = normalizeMimeType(part.getContentType());
        if (normalizedRawType != null && !normalizedRawType.isBlank() && !isGenericBinaryMimeType(normalizedRawType)) {
            return normalizedRawType;
        }
        String inferred = inferContentTypeFromFilename(filename);
        if (inferred != null && !inferred.isBlank()) {
            return inferred;
        }
        return normalizedRawType == null || normalizedRawType.isBlank()
                ? "application/octet-stream"
                : normalizedRawType;
    }

    private String normalizeMimeType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return null;
        }
        int separatorIndex = rawContentType.indexOf(';');
        String mimeType = separatorIndex > 0 ? rawContentType.substring(0, separatorIndex) : rawContentType;
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isGenericBinaryMimeType(String mimeType) {
        return "application/octet-stream".equalsIgnoreCase(mimeType);
    }

    private String decodeMimeText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            return MimeUtility.decodeText(value);
        } catch (Exception e) {
            log.debug("Failed to decode MIME text, using raw value: {}", value, e);
            return value;
        }
    }

    private String inferContentTypeFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String inferred = URLConnection.guessContentTypeFromName(filename);
        if (inferred != null && !inferred.isBlank()) {
            return normalizeMimeType(inferred);
        }
        int extensionSeparator = filename.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == filename.length() - 1) {
            return null;
        }
        return MIME_TYPES_BY_EXTENSION.get(filename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT));
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private void addRemoteAttachments(MailRaw mail, Message message) {
        List<MailRawAttachment> remoteAttachments = qqLargeAttachmentDetector.detect(mail.getTextBody(), mail.getHtmlBody());
        if (remoteAttachments.isEmpty()) {
            return;
        }
        mail.getAttachments().addAll(remoteAttachments);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            message.writeTo(outputStream);
            mail.setRawMimeBytes(outputStream.toByteArray());
        } catch (Exception e) {
            log.warn("Failed to capture raw MIME for QQ large attachment mail: {}", mail.getMessageId(), e);
        }
    }
}
