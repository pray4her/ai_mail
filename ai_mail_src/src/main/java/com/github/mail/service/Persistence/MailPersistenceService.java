package com.github.mail.service.Persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.mail.repo.Mail.domain.*;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.mapper.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final MailMessageMapper mailMessageMapper;
    private final MailProcessingRecordMapper processingRecordMapper;

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
        try {
            long startTime = System.currentTimeMillis();

            // 1. 检查邮件是否已存在（使用 message_id，主要幂等性保证）
            if (messageExists(accountId, mailRaw.getMessageId())) {
//                logOperation(accountId, null, "FETCH", "DUPLICATE", 0, null,
//                        "Message already exists");
                return PersistenceResult.duplicate("Message already exists: " + mailRaw.getMessageId());
            }

            // 2. 解析邮件为实体
            MailMessage message = convertToMailMessage(mailRaw, accountId);

            // 3. 插入邮件主记录
            mailMessageMapper.insert(message);
            log.info("Persisted mail message: {} from {} with id {}",
                    message.getSubject(), message.getFromEmail(), message.getId());

            // 4. 插入附件（若有，暂不支持 - 待 MailRaw 扩展）
            // TODO: 当 MailRaw 支持附件时再实现

            // 5. 创建处理记录
            MailProcessingRecord processingRecord = createInitialProcessingRecord(
                    message, accountId
            );
            processingRecordMapper.insert(processingRecord);

            // 6. 记录成功日志 暂不记录日志
//            long duration = System.currentTimeMillis() - startTime;
//            logOperation(accountId, message.getId(), "PERSIST", "SUCCESS", duration,
//                    null, null);

            return PersistenceResult.success(message.getId());

        } catch (Exception e) {
            log.error("Failed to persist email: {}", mailRaw.getMessageId(), e);
            // 异常会自动触发事务回滚
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
    private boolean messageExists(Long accountId, String messageId) {
        Long count = mailMessageMapper.selectCount(
                Wrappers.lambdaQuery(MailMessage.class)
                        .eq(MailMessage::getMailAccountId, accountId)
                        .eq(MailMessage::getMessageId, messageId)
        );
        return count != null && count > 0;
    }


    /**
     * 将原始邮件转换为 MailMessage 实体
     */
    private MailMessage convertToMailMessage(MailRaw mailRaw, Long accountId) {
        MailMessage message = new MailMessage();
        message.setMailAccountId(accountId);
        message.setMessageId(mailRaw.getMessageId());
        message.setSubject(mailRaw.getSubject());
        message.setFromEmail(mailRaw.getFrom());

        // 转换收件人列表为 JSON
        if (mailRaw.getTo() != null && !mailRaw.getTo().isEmpty()) {
            message.setToEmails(mailRaw.getTo());
        }

        String text = Jsoup.parse(mailRaw.getHtmlBody()).text();
        // 分离 HTML 和纯文本
        message.setBodyHtml(mailRaw.getHtmlBody());
        message.setBodyText(text);

        // 邮件元数据
        if (mailRaw.getSentDate() != null) {
            message.setSentAt(new java.sql.Timestamp(mailRaw.getSentDate().getTime()).toLocalDateTime());
        }
        message.setReceivedAt(LocalDateTime.now());
        message.setHasAttachment(mailRaw.isHasAttachment() ? 1 : 0);

        // 线程 ID
        message.setThreadId(mailRaw.getThreadId());

        message.setIsRead(0);
        message.setIsDeleted(0);

        return message;
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
