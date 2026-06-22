package com.github.mail.service.Persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.mail.model.config.MailConfig;
import com.github.mail.model.config.Properties.MailServerProperties;
import com.github.mail.repo.Mail.domain.*;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.dto.MailRawAttachment;
import com.github.mail.repo.Mail.mapper.*;
import com.github.mail.service.File.MinioStorageService;
import com.github.mail.service.ai.AiInputAttachment;
import com.github.mail.utils.TikaDocumentParser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 邮件持久化服务
 * <p>
 * 职责：
 * 1. 解析原始邮件数据为实体
 * 2. 事务性入库邮件、附件、处理记录
 * 3. 支持幂等的邮件插入（message_id 去重）
 * 4. 初始化处理流程状态（创建初始处理记录）
 * <p>
 * 设计原则：
 * - 邮件入库目标：审计、回溯、客服查看、人工介入
 * - 不对邮件进行分片（chunk 仅用于知识库文档）
 * - 单一的幂等性保护：message_id 唯一约束
 * - 附件作为独立表，支持异步扫描与下载
 * - 完整的事务性，任何步骤失败都会回滚
 *
 * @author Asteries
 */
// TODO：目前入库未启用，未测试，若要实现邮件上下文则需要入库，根据邮箱查询一定时间内的历史邮件
@Slf4j
@Service
@RequiredArgsConstructor
public class MailPersistenceService {

    private static final String STORAGE_TYPE_MINIO = "MINIO";
    private static final String ATTACHMENT_KIND_MINIO = "MINIO";
    private static final String ATTACHMENT_KIND_REMOTE_LINK = "REMOTE_LINK";

    private final MailMessageMapper mailMessageMapper;
    private final MailAttachmentMapper mailAttachmentMapper;
    private final MailProcessingRecordMapper processingRecordMapper;
    private final MailAccountMapper mailAccountMapper;
    private final MinioStorageService minioStorageService;
    private final TikaDocumentParser tikaDocumentParser;

    /**
     * 单个邮件的幂等入库
     * <p>
     * 流程：
     * 1. 检查 message_id 是否已存在（主要幂等性保证）
     * 2. 解析邮件为实体
     * 3. 事务性插入：邮件 → 附件 → 处理记录
     * 4. 记录日志
     *
     * @param mailRaw   原始邮件数据
     * @param accountId 邮箱账户ID
     * @return 入库结果（成功/重复/失败）
     */
    @Transactional(rollbackFor = Exception.class)
    public PersistenceResult persistEmail(MailRaw mailRaw, Long accountId) {
        return persistEmail(mailRaw, accountId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public PersistenceResult persistHistoryEmail(MailRaw mailRaw, Long accountId) {
        mailRaw.setHistory(true);
        return persistEmail(mailRaw, accountId, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public PersistenceResult persistEmail(MailRaw mailRaw, Long accountId, boolean createProcessingRecord) {
        try {
            MailMessage existingMessage = findExistingMessage(accountId, mailRaw);
            if (existingMessage != null) {
                return PersistenceResult.duplicate(existingMessage.getId(), "Message already exists: " + mailRaw.getMessageId());
            }

            MailMessage message = convertToMailMessage(mailRaw, accountId);
            mailMessageMapper.insert(message);
            persistRawMimeIfPresent(mailRaw, message);
            log.info("Persisted mail message: {} from {} with id {}",
                    message.getSubject(), message.getFromEmail(), message.getId());

            persistAttachments(mailRaw, message);

            if (createProcessingRecord) {
                MailProcessingRecord processingRecord = createInitialProcessingRecord(message, accountId);
                processingRecordMapper.insert(processingRecord);
            }

            return PersistenceResult.success(message.getId());
        } catch (Exception e) {
            log.error("Failed to persist email: {}", mailRaw.getMessageId(), e);
            throw new RuntimeException("Email persistence failed", e);
        }
    }

    /**
     * 批量幂等入库邮件
     *
     * @param mailRawList 原始邮件列表
     * @param accountId   邮箱账户ID
     * @return 入库统计结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchPersistenceResult persistEmailsBatch(List<MailRaw> mailRawList, Long accountId) {
        int successCount = 0;
        int duplicateCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();

        for (MailRaw mailRaw : mailRawList) {
            try {
                PersistenceResult result = persistEmail(mailRaw, accountId);
                if (result.isSuccess()) {
                    successCount++;
                } else if (result.isDuplicate()) {
                    duplicateCount++;
                } else {
                    failureCount++;
                    errors.add(result.getErrorMessage());
                }
            } catch (Exception e) {
                failureCount++;
                errors.add(e.getMessage());
                log.error("Failed to persist email in batch: {}", mailRaw.getMessageId(), e);
            }
        }

        return new BatchPersistenceResult(successCount, duplicateCount, failureCount, errors);
    }

    /**
     * 检查邮件是否已存在（通过 message_id）
     */
    public Optional<Long> findAccountId(MailConfig.Imap imapConfig) {
        return findAccount(imapConfig).map(MailAccount::getId);
    }

    public Optional<MailAccount> findAccount(MailConfig.Imap imapConfig) {
        MailServerProperties.Imap properties = MailServerProperties.fromMailConfig(imapConfig);
        MailAccount account = mailAccountMapper.selectOne(
                Wrappers.lambdaQuery(MailAccount.class)
                        .eq(MailAccount::getEmail, properties.getUserName())
                        .eq(MailAccount::getImapHost, properties.getHost())
                        .eq(MailAccount::getImapPort, properties.getPort())
        );
        return Optional.ofNullable(account);
    }

    @Transactional(rollbackFor = Exception.class)
    public MailAccount ensureAccount(MailConfig.Imap imapConfig) {
        Optional<MailAccount> existingAccount = findAccount(imapConfig);
        if (existingAccount.isPresent()) {
            return existingAccount.get();
        }

        MailServerProperties.Imap properties = MailServerProperties.fromMailConfig(imapConfig);
        LocalDateTime now = LocalDateTime.now();
        MailAccount account = new MailAccount();
        account.setEmail(properties.getUserName());
        account.setImapHost(properties.getHost());
        account.setImapPort(properties.getPort());
        account.setUsername(properties.getUserName());
        account.setPassword(imapConfig.getPassword());
        account.setUseSsl(properties.isSsl() ? 1 : 0);
        account.setLastSyncAt(now);
        account.setLastSyncUid(0L);
        account.setUidValidity(0L);
        account.setHistorySynced(0);
        account.setIsDeleted(0);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        mailAccountMapper.insert(account);
        return account;
    }

    public List<AiInputAttachment> loadGenerationAttachments(Long mailMessageId) {
        List<MailAttachment> attachments = mailAttachmentMapper.selectList(
                Wrappers.lambdaQuery(MailAttachment.class)
                        .eq(MailAttachment::getMailMessageId, mailMessageId)
                        .orderByAsc(MailAttachment::getId)
        );
        return attachments.stream()
                .map(this::toAiInputAttachment)
                .toList();
    }

    public void markReplyDraftSaved(Long mailMessageId, String draftFolder, String replyContent) {
        MailProcessingRecord record = latestRecord(mailMessageId);
        if (record == null) {
            return;
        }
        record.setReplyStatus("DRAFT");
        record.setReplyDraftFolder(draftFolder);
        record.setReplyContent(replyContent);
        record.setProcessedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        processingRecordMapper.updateById(record);
    }

    public void markReplyFailed(Long mailMessageId, String errorMessage) {
        if (mailMessageId == null) {
            return;
        }
        MailProcessingRecord record = latestRecord(mailMessageId);
        if (record == null) {
            return;
        }
        record.setReplyStatus("FAILED");
        record.setErrorMessage(errorMessage);
        record.setProcessedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        processingRecordMapper.updateById(record);
    }

    private MailProcessingRecord latestRecord(Long mailMessageId) {
        return processingRecordMapper.selectOne(
                Wrappers.lambdaQuery(MailProcessingRecord.class)
                        .eq(MailProcessingRecord::getMailMessageId, mailMessageId)
                        .orderByDesc(MailProcessingRecord::getId)
                        .last("limit 1")
        );
    }

    public void markHistorySyncStarted(Long accountId) {
        LocalDateTime now = LocalDateTime.now();
        mailAccountMapper.update(null, Wrappers.lambdaUpdate(MailAccount.class)
                .eq(MailAccount::getId, accountId)
                .set(MailAccount::getHistorySyncStartedAt, now)
                .set(MailAccount::getHistorySyncCompletedAt, null)
                .set(MailAccount::getHistorySyncError, null)
                .set(MailAccount::getUpdatedAt, now));
    }

    public void markHistorySyncCompleted(Long accountId) {
        LocalDateTime now = LocalDateTime.now();
        mailAccountMapper.update(null, Wrappers.lambdaUpdate(MailAccount.class)
                .eq(MailAccount::getId, accountId)
                .set(MailAccount::getHistorySynced, 1)
                .set(MailAccount::getHistorySyncCompletedAt, now)
                .set(MailAccount::getHistorySyncError, null)
                .set(MailAccount::getUpdatedAt, now));
    }

    public void markHistorySyncFailed(Long accountId, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        mailAccountMapper.update(null, Wrappers.lambdaUpdate(MailAccount.class)
                .eq(MailAccount::getId, accountId)
                .set(MailAccount::getHistorySynced, 0)
                .set(MailAccount::getHistorySyncError, errorMessage)
                .set(MailAccount::getUpdatedAt, now));
    }

    private MailMessage findExistingMessage(Long accountId, MailRaw mailRaw) {
        if (mailRaw.getMessageId() != null && !mailRaw.getMessageId().isBlank()) {
            MailMessage byMessageId = mailMessageMapper.selectOne(
                    Wrappers.lambdaQuery(MailMessage.class)
                            .eq(MailMessage::getMailAccountId, accountId)
                            .eq(MailMessage::getMessageId, mailRaw.getMessageId())
                            .last("limit 1")
            );
            if (byMessageId != null) {
                return byMessageId;
            }
        }
        if (mailRaw.getFolderName() == null || mailRaw.getImapUid() == null || mailRaw.getFolderUidValidity() == null) {
            return null;
        }
        return mailMessageMapper.selectOne(
                Wrappers.lambdaQuery(MailMessage.class)
                        .eq(MailMessage::getMailAccountId, accountId)
                        .eq(MailMessage::getFolderName, mailRaw.getFolderName())
                        .eq(MailMessage::getImapUid, mailRaw.getImapUid())
                        .eq(MailMessage::getFolderUidValidity, mailRaw.getFolderUidValidity())
                        .last("limit 1")
        );
    }


    /**
     * 将原始邮件转换为 MailMessage 实体
     */
    private MailMessage convertToMailMessage(MailRaw mailRaw, Long accountId) {
        MailMessage message = new MailMessage();
        message.setMailAccountId(accountId);
        message.setMessageId(mailRaw.getMessageId());
        message.setFolderName(mailRaw.getFolderName());
        message.setImapUid(mailRaw.getImapUid());
        message.setFolderUidValidity(mailRaw.getFolderUidValidity());
        message.setSubject(mailRaw.getSubject());
        message.setFromEmail(mailRaw.getFrom());

        // 转换收件人列表为 JSON
        if (mailRaw.getTo() != null && !mailRaw.getTo().isEmpty()) {
            message.setToEmails(mailRaw.getTo());
        }
        message.setCcEmails(mailRaw.getCc());
        message.setBccEmails(mailRaw.getBcc());
        message.setInReplyTo(mailRaw.getInReplyTo());
        message.setMailReferences(mailRaw.getMailReferences());

        String text = mailRaw.getTextBody();
        if ((text == null || text.isBlank()) && mailRaw.getHtmlBody() != null) {
            text = Jsoup.parse(mailRaw.getHtmlBody()).text();
        }
        message.setBodyHtml(mailRaw.getHtmlBody());
        message.setBodyText(text);

        if (mailRaw.getSentDate() != null) {
            message.setSentAt(new java.sql.Timestamp(mailRaw.getSentDate().getTime()).toLocalDateTime());
        }
        message.setReceivedAt(LocalDateTime.now());
        message.setHasAttachment(mailRaw.isHasAttachment() ? 1 : 0);
        message.setAttachmentCount(mailRaw.getAttachmentCount());
        message.setThreadId(mailRaw.getThreadId());
        message.setDirection(resolveDirection(mailRaw, accountId));
        message.setIsHistory(mailRaw.isHistory() ? 1 : 0);
        message.setIsRead(0);
        message.setIsDeleted(0);

        return message;
    }

    private String resolveDirection(MailRaw mailRaw, Long accountId) {
        MailAccount account = mailAccountMapper.selectById(accountId);
        String accountEmail = account == null ? null : account.getEmail();
        if (accountEmail != null && mailRaw.getFrom() != null && accountEmail.equalsIgnoreCase(mailRaw.getFrom())) {
            return "OUTBOUND";
        }
        return "INBOUND";
    }

    private void persistRawMimeIfPresent(MailRaw mailRaw, MailMessage message) {
        byte[] rawMimeBytes = mailRaw.getRawMimeBytes();
        if (rawMimeBytes == null || rawMimeBytes.length == 0) {
            return;
        }
        String storagePath = "mail-raw/" + message.getId() + ".eml";
        minioStorageService.uploadFile(storagePath, rawMimeBytes, "message/rfc822");
        message.setRawMimeStoragePath(storagePath);
        message.setUpdatedAt(LocalDateTime.now());
        mailMessageMapper.updateById(message);
    }

    private void persistAttachments(MailRaw mailRaw, MailMessage message) {
        List<MailRawAttachment> attachments = mailRaw.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        for (MailRawAttachment attachment : attachments) {
            if (ATTACHMENT_KIND_REMOTE_LINK.equalsIgnoreCase(attachment.getAttachmentKind())) {
                persistRemoteAttachment(message, attachment);
                continue;
            }
            String storagePath = buildAttachmentStoragePath(message.getId(), attachment);
            minioStorageService.uploadFile(storagePath, attachment.getBytes(), attachment.getContentType());
            attachment.setStoragePath(storagePath);
            attachment.setStorageType(STORAGE_TYPE_MINIO);
            attachment.setAttachmentKind(ATTACHMENT_KIND_MINIO);
            attachment.setFallbackExtractedText(extractFallbackText(attachment));

            MailAttachment entity = new MailAttachment();
            entity.setMailMessageId(message.getId());
            entity.setFilename(attachment.getFilename());
            entity.setContentType(attachment.getContentType());
            entity.setContentLength(attachment.getSize());
            entity.setContentHash(attachment.getContentHash());
            entity.setStoragePath(storagePath);
            entity.setStorageType(STORAGE_TYPE_MINIO);
            entity.setAttachmentKind(ATTACHMENT_KIND_MINIO);
            entity.setIsDownloaded(1);
            entity.setIsScanned(0);
            entity.setCreatedAt(LocalDateTime.now());
            mailAttachmentMapper.insert(entity);
        }
    }

    private void persistRemoteAttachment(MailMessage message, MailRawAttachment attachment) {
        MailAttachment entity = new MailAttachment();
        entity.setMailMessageId(message.getId());
        entity.setFilename(attachment.getFilename());
        entity.setContentType(attachment.getContentType());
        entity.setContentLength(attachment.getSize());
        entity.setContentHash(attachment.getContentHash());
        entity.setStoragePath(attachment.getExternalUrl());
        entity.setStorageType(ATTACHMENT_KIND_REMOTE_LINK);
        entity.setAttachmentKind(ATTACHMENT_KIND_REMOTE_LINK);
        entity.setExternalUrl(attachment.getExternalUrl());
        entity.setExpiresAt(attachment.getExpiresAt());
        entity.setRemark(attachment.getRemark());
        entity.setIsDownloaded(0);
        entity.setIsScanned(0);
        entity.setCreatedAt(LocalDateTime.now());
        mailAttachmentMapper.insert(entity);
    }

    private String buildAttachmentStoragePath(Long mailMessageId, MailRawAttachment attachment) {
        String safeFilename = attachment.getFilename() == null || attachment.getFilename().isBlank()
                ? "attachment.bin"
                : attachment.getFilename().replaceAll("[\\\\/:*?\"<>|]+", "_");
        return "mail-attachments/" + mailMessageId + "/" + attachment.getContentHash() + "-" + safeFilename;
    }

    private String extractFallbackText(MailRawAttachment attachment) {
        if (!supportsTextFallback(attachment.getContentType(), attachment.getFilename())) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(attachment.getBytes())) {
            String extracted = tikaDocumentParser.extractText(inputStream, attachment.getFilename());
            return extracted == null || extracted.isBlank() ? null : extracted;
        } catch (Exception e) {
            log.warn("Failed to extract attachment fallback text: {}", attachment.getFilename(), e);
            return null;
        }
    }

    private boolean supportsTextFallback(String contentType, String filename) {
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedType.startsWith("image/")) {
            return false;
        }
        if (normalizedType.startsWith("text/")) {
            return true;
        }
        if (normalizedType.equals("application/pdf")) {
            return true;
        }
        String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lowerFilename.endsWith(".doc")
                || lowerFilename.endsWith(".docx")
                || lowerFilename.endsWith(".rtf")
                || lowerFilename.endsWith(".txt");
    }

    private AiInputAttachment toAiInputAttachment(MailAttachment attachment) {
        if (ATTACHMENT_KIND_REMOTE_LINK.equalsIgnoreCase(attachment.getAttachmentKind())) {
            String fallbackText = String.join("\n",
                    "远程附件链接: " + Objects.toString(attachment.getExternalUrl(), ""),
                    "备注: " + Objects.toString(attachment.getRemark(), "")
            ).trim();
            return new AiInputAttachment(
                    attachment.getId(),
                    attachment.getFilename(),
                    attachment.getContentType(),
                    attachment.getExternalUrl(),
                    attachment.getContentHash(),
                    fallbackText
            );
        }
        String fallbackExtractedText = null;
        if (supportsTextFallback(attachment.getContentType(), attachment.getFilename())) {
            byte[] bytes = minioStorageService.readBytes(attachment.getStoragePath());
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
                fallbackExtractedText = tikaDocumentParser.extractText(inputStream, attachment.getFilename());
            } catch (Exception e) {
                log.warn("Failed to load attachment text from MinIO: {}", attachment.getStoragePath(), e);
            }
        }
        return new AiInputAttachment(
                attachment.getId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getStoragePath(),
                attachment.getContentHash(),
                fallbackExtractedText
        );
    }


    /**
     * 创建初始处理记录
     */
    private MailProcessingRecord createInitialProcessingRecord(MailMessage message, Long accountId) {
        return MailProcessingRecord.builder()
                .mailMessageId(message.getId())
                .mailAccountId(accountId)
                .messageIdSnapshot(message.getMessageId())
                .subject(message.getSubject())
                .fromEmail(message.getFromEmail())
                .threadId(message.getThreadId())
                .replyStatus("PENDING")
                .isDeleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 入库结果 VO
     */
    @Getter
    public static class PersistenceResult {
        private final boolean success;
        private final boolean duplicate;
        private final Long messageId;
        private final String errorMessage;

        public static PersistenceResult success(Long messageId) {
            return new PersistenceResult(true, false, messageId, null);
        }

        public static PersistenceResult duplicate(Long messageId, String reason) {
            return new PersistenceResult(false, true, messageId, reason);
        }

        public static PersistenceResult duplicate(String reason) {
            return new PersistenceResult(false, true, null, reason);
        }

        public static PersistenceResult failure(String errorMsg) {
            return new PersistenceResult(false, false, null, errorMsg);
        }

        public PersistenceResult(boolean success, boolean duplicate, Long messageId, String errorMessage) {
            this.success = success;
            this.duplicate = duplicate;
            this.messageId = messageId;
            this.errorMessage = errorMessage;
        }

    }

    /**
     * 批量入库结果统计
     */
    public static class BatchPersistenceResult {
        public int successCount;
        public int duplicateCount;
        public int failureCount;
        public List<String> errors;

        public BatchPersistenceResult(int successCount, int duplicateCount, int failureCount, List<String> errors) {
            this.successCount = successCount;
            this.duplicateCount = duplicateCount;
            this.failureCount = failureCount;
            this.errors = errors;
        }

        @Override
        public String toString() {
            return String.format(
                    "BatchPersistenceResult{success=%d, duplicate=%d, failure=%d, errors=%s}",
                    successCount, duplicateCount, failureCount, errors
            );
        }
    }
}
