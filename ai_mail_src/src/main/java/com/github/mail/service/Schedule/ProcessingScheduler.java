package com.github.mail.service.Schedule;

import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.Mail.domain.MailAccount;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.mapper.MailAccountMapper;
import com.github.mail.service.Fetcher.MailFetchService;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleOutcome;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleResult;
import com.github.mail.service.KnowledgeBase.KbDocumentLifecycleService;
import com.github.mail.service.Persistence.MailPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件处理管道服务
 * <p>
 * 正确的流程：
 * 1. 从 IMAP 增量拷取邮件（支持幂等）
 * 2. 解析并入库到 MySQL（事务保护，仅为审计/回溯/客服查看）
 * 3. 等待意图识别（可选）或直接生成回复
 * 4. 记录处理日志（审计链路）
 * <p>
 * 设计原则：
 * - 邮件仅处理存储，不需要分片
 * - 分片仅用于知识库文档（使用 Apache Tika 解析）//TODO：tika仅仅解析带有文本流的文档，图片型文档需OCR
 * - 流程清晰：拷取 → 入库 → 意图识别 → 回复生成
 * - 邮件不参与 RAG 构建（邮件=查询请求，知识库=知识资产）
 *
 * @author Asteries
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mail.auto-process", name = "enabled", havingValue = "true")
public class ProcessingScheduler {

    private final MailFetchService fetchService;
    private final MailPersistenceService persistenceService;
    private final KbDocumentLifecycleService lifecycleService;
    private final MailAccountMapper mailAccountMapper;




    /**
     * 定时任务：处理知识库文档 TODO: 目前暂时取消定时处理文档，在上传成功时自动调用
     * <p>
     * 1. 获取所有待处理的文档
     * 2. 遍历文档列表，对每个文档进行解析
     * 3. 更新文档状态为已处理
     * 4. 记录处理日志
     */
    //@Scheduled(initialDelay = 5000, fixedRate = 900000)
    public void parseKbDocumentList() {
        log.info("========== 开始自动处理知识库文档 ==========");

        List<KbDocument> pendingDocs = lifecycleService.findPendingDocuments();

        if (pendingDocs.isEmpty()) {
            log.info("没有待处理的文档");
            return;
        }

        List<Long> failedDocs = new ArrayList<>();
        for (KbDocument doc : pendingDocs) {
            try {
                lifecycleService.retryProcessing(doc.getId());
            } catch (Exception e) {
                failedDocs.add(doc.getId());
                log.error("文档处理失败 {}: {}", doc.getId(), e.getMessage(), e);
            }
        }

        log.info("处理完成，总数: {}, 失败: {}", pendingDocs.size(), failedDocs.size());
    }


    public void parseKbDocumentList(KbDocument doc) {
        lifecycleService.retryProcessing(doc.getId());
    }

    /**
     * 定时任务：清理解析错误的文件
     * <p>
     * 定期检测状态为9（解析错误）的文档，并从数据库、MinIO和ES中删除
     */
    @Scheduled(initialDelay = 30000, fixedRate = 3600000) // 初始延迟30秒，每小时执行一次
    public void cleanupFailedDocuments() {
        log.info("========== 开始清理解析错误的文档 ==========");
        
        try {
            // 查询所有状态为9（解析错误）的文档
            List<KbDocumentLifecycleResult> results = lifecycleService.cleanupFailedDocuments();

            if (results.isEmpty()) {
                log.info("没有需要清理的解析错误文档");
                return;
            }
            
            long successCount = results.stream()
                    .filter(result -> result.outcome() == KbDocumentLifecycleOutcome.SUCCESS)
                    .count();
            long failCount = results.size() - successCount;
            
            log.info("清理完成，成功: {}, 失败: {}", successCount, failCount);
            
        } catch (Exception e) {
            log.error("清理解析错误文档过程中发生异常", e);
        }
        
        log.info("========== 清理解析错误的文档结束 ==========");
    }
    
}
