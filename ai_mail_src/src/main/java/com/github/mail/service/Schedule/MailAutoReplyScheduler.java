package com.github.mail.service.Schedule;

import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.service.Fetcher.MailFetchService;
import com.github.mail.service.KnowledgeBase.RagService;
import com.github.mail.service.MailOperation.MailSendService;
import com.github.mail.service.ai.AiGenerationRequest;
import com.github.mail.service.ai.AiGenerationResult;
import com.github.mail.service.ai.AiGenerationService;
import com.github.mail.utils.TikaDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 邮件自动回复定时任务调度器
 * <p>
 * 功能特性：
 * 1. 智能动态间隔调整：无新邮件时逐步延长轮询间隔（1分钟 → 5分钟 → 10分钟）
 * 2. 批量处理优化：批量embedding调用，提升效率
 * 3. 预留IMAP IDLE推送接口：支持后续接入服务器推送机制
 *
 * @author Asteries
 * @date 2025/12/30
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mail.auto-reply", name = "enabled", havingValue = "true")
public class MailAutoReplyScheduler {

    private final MailFetchService mailFetchService;
    private final RagService ragService;
    private final AiGenerationService aiGenerationService;
    private final MailSendService mailSendService;
    private final TikaDocumentParser tikaDocumentParser;
    private final MailConfig mailConfig;


    // 从配置文件读取参数

    private MailConfig mail() {
        return mailConfig;
    }

    private int intervalLevel1() {
        return mail().getAutoReply().getInterval().getLevel1();
    }

    private int intervalLevel2() {
        return mail().getAutoReply().getInterval().getLevel2();
    }

    private int intervalLevel3() {
        return mail().getAutoReply().getInterval().getLevel3();
    }

    private int ragTopK() {
        return mail().getRag().getTopK();
    }

    private int emptyThreshold1() {
        return mail().getAutoReply().getThreshold().getEmptyCount1();
    }

    private int emptyThreshold2() {
        return mail().getAutoReply().getThreshold().getEmptyCount2();
    }

    private double ragMinScore() {
        return mail().getRag().getMinScore();
    }

    private String draftFolder() {
        return mail().getAutoReply().getDraftFolder();
    }


    // 连续无邮件次数
    private final AtomicInteger consecutiveEmptyCount = new AtomicInteger(0);
    // 下次执行延迟（秒），初始1分半
    private final AtomicLong nextExecutionDelay = new AtomicLong(90);

    /**
     * 定时任务：每1分半执行一次（实际间隔由动态机制控制）
     * <p>
     * 策略说明：
     * - 有新邮件：立即处理，重置为1分钟间隔
     * - 连续3次无邮件：延长至5分钟
     * - 连续6次无邮件：延长至10分钟
     */
    @Scheduled(fixedDelay = 90, timeUnit = TimeUnit.SECONDS, initialDelay = 10)
    public void autoGenerateReply() {
        try {
            // 动态间隔检查：如果未到执行时间，跳过本次
            if (!shouldExecuteNow()) {
                log.debug("未到执行时间，跳过本次轮询（下次执行需等待 {} 秒）", nextExecutionDelay.get());
                return;
            }

            log.info("========== 开始自动邮件回复任务 ==========");


            // 第一步：读取邮箱配置
            List<MailConfig.Imap> imapList = mailConfig.getImapList();

            boolean hasAnyMail = false;

            for (MailConfig.Imap imap : imapList) {
                List<MailRaw> fetchMails = mailFetchService.fetchToAiReply(imap);

                if (fetchMails != null && !fetchMails.isEmpty()) {
                    hasAnyMail = true;
                    log.info("已找到 {} 条待处理邮件", fetchMails.size());
                    handleNewMails(fetchMails, imap);
                }else{
                    log.info("邮箱{}无待处理邮件",imap.getUsername());
                }

            }

            if (!hasAnyMail) {
                handleEmptyMailbox();
            }

        } catch (Exception e) {
            log.error("自动邮件回复任务执行失败", e);
        }
    }

    /**
     * 判断是否应该执行本次任务（基于动态间隔）
     */
    private boolean shouldExecuteNow() {
        long currentDelay = nextExecutionDelay.get();
        if (currentDelay <= intervalLevel1()) {
            // 一分半分钟间隔始终执行
            return true;
        }

        // 5分钟或10分钟间隔需要模拟计数器
        int emptyCount = consecutiveEmptyCount.get();
        if (currentDelay == intervalLevel2()) {
            // 每5次执行一次
            return emptyCount % 5 == 0;
        } else if (currentDelay == intervalLevel3()) {
            // 每10次执行一次
            return emptyCount % 10 == 0;
        }
        return true;
    }

    /**
     * 处理无新邮件情况：动态延长轮询间隔
     */
    private void handleEmptyMailbox() {
        int emptyCount = consecutiveEmptyCount.incrementAndGet();

        if (emptyCount >= emptyThreshold2()) {
            nextExecutionDelay.set(intervalLevel3());
            log.info("连续 {} 次无新邮件，轮询间隔延长至 {} 分钟", emptyCount, intervalLevel3() / 60);
        } else if (emptyCount >= emptyThreshold1()) {
            nextExecutionDelay.set(intervalLevel2());
            log.info("连续 {} 次无新邮件，轮询间隔延长至 {} 分钟", emptyCount, intervalLevel2() / 60);
        } else {
            log.info("无待处理邮件（连续 {} 次）", emptyCount);
        }
    }

    /**
     * 处理新邮件：批量生成AI回复
     */
    private void handleNewMails(List<MailRaw> fetchMails, MailConfig.Imap imap) {
        // 重置间隔为1分钟
        consecutiveEmptyCount.set(0);
        nextExecutionDelay.set(intervalLevel1());

        log.info("检测到 {} 封新邮件，开始生成AI回复（重置轮询间隔为1分钟）", fetchMails.size());

        try {
            // 批量提取用户查询
            List<String> userQueries = fetchMails.stream()
                    .map(mail -> tikaDocumentParser.getEffectiveText(mail.getTextBody(), mail.getHtmlBody()))
                    .toList();

            // 批量 RAG 检索（优化：一次性embedding调用）
            List<List<RagChunk>> batchChunks = ragService.batchRetrieveRagChunks(userQueries, ragTopK(), ragMinScore());

            // 逐封邮件生成回复
            for (int i = 0; i < fetchMails.size(); i++) {
                processMailReply(fetchMails.get(i), userQueries.get(i), batchChunks.get(i), i + 1, imap);
            }

            log.info("========== 自动邮件回复任务完成 ==========");

        } catch (Exception e) {
            log.error("批量生成AI回复失败", e);
        }
    }

    /**
     * 处理单封邮件的AI回复生成
     */
    private void processMailReply(MailRaw mail, String userQuery,
                                  List<RagChunk> relevantChunks,
                                  int index,
                                  MailConfig.Imap imapConfig) {
        try {
            log.info("---------- 邮件 #{} 开始生成 ----------", index);
            log.info("发件人: {}, 主题: {}", mail.getFrom(), mail.getSubject());
            log.info("检索到 {} 个相关知识片段", relevantChunks.size());

            AiGenerationResult generationResult = aiGenerationService.generate(new AiGenerationRequest(
                    null,
                    userQuery,
                    relevantChunks,
                    Map.of(
                            "entrypoint", "scheduler",
                            "messageId", mail.getMessageId() == null ? "" : mail.getMessageId(),
                            "subject", mail.getSubject() == null ? "" : mail.getSubject(),
                            "from", mail.getFrom() == null ? "" : mail.getFrom()
                    )
            ));
            String aiReplyContent = generationResult.content();

            // 保存为草稿到配置的文件夹
            mailSendService.saveDraftToFolder(
                    mail.getFrom(),
                    mail.getSubject(),
                    aiReplyContent,
                    draftFolder(),
                    imapConfig
            );

            log.info("邮件 #{} 回复生成成功并保存至草稿", index);

        } catch (Exception e) {
            log.error("邮件 #{} 处理失败, MessageID={}", index, mail.getMessageId(), e);
        }
    }

    /**
     * 【预留接口】启用IMAP IDLE推送机制
     * <p>
     * 使用方式：
     * 1. 需要邮件服务器支持 IMAP IDLE 扩展
     * 2. 调用此方法启动监听线程
     * 3. 有新邮件时自动触发 handleNewMailPush() 回调
     * <p>
     * 注意：启用后会禁用定时轮询机制
     */
    public void enableImapIdlePush() {
        log.warn("IMAP IDLE推送机制尚未实现，请确保邮件服务器支持此功能");

        // TODO: 实现IMAP IDLE监听
        // 示例伪代码：
        // IMAPFolder folder = ...;
        // folder.addMessageCountListener(new MessageCountAdapter() {
        //     @Override
        //     public void messagesAdded(MessageCountEvent e) {
        //         Message[] messages = e.getMessages();
        //         handleNewMailPush(Arrays.asList(messages));
        //     }
        // });
        // folder.idle();
    }

    /**
     * 【预留接口】IMAP IDLE推送回调处理
     *
     * @param newMessages 新邮件列表
     */
    private void handleNewMailPush(List<MailRaw> newMessages) {
        log.info("【IDLE推送】收到 {} 封新邮件", newMessages.size());
        handleNewMails(newMessages,new MailConfig.Imap());
    }
}
