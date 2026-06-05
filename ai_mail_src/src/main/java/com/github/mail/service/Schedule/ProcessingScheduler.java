package com.github.mail.service.Schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.mail.model.config.Properties.MinIOProperties;
import com.github.mail.repo.KbDocument.domain.DocumentTag;
import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KbDocument.mapper.DocumentTagMapper;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.repo.KnowledgeBase.dao.ElasticsearchChunkIndexRepository;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.domain.KbVectorIndex;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.repo.KnowledgeBase.mapper.KbVectorIndexMapper;
import com.github.mail.repo.Mail.domain.MailAccount;
import com.github.mail.repo.Mail.dto.MailRaw;
import com.github.mail.repo.Mail.mapper.MailAccountMapper;
import com.github.mail.service.Fetcher.MailFetchService;
import com.github.mail.service.File.MinioStorageService;
import com.github.mail.service.KnowledgeBase.KbChunkingService;
import com.github.mail.service.KnowledgeBase.KbDocumentService;
import com.github.mail.service.KnowledgeBase.KbEmbeddingService;
import com.github.mail.service.Persistence.MailPersistenceService;
import com.github.mail.utils.PathUtil;
import com.github.mail.utils.TikaDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
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
@ConditionalOnProperty(prefix = "mail.auto-process", name = "enabled", havingValue = "true")
public class ProcessingScheduler {

    private final MailFetchService fetchService;
    private final MailPersistenceService persistenceService;
    private final KbDocumentService kbDocumentService;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkingService kbChunkingService;
    private final KbEmbeddingService kbEmbeddingService;
    private final MailAccountMapper mailAccountMapper;
    private final TikaDocumentParser tikaDocumentParser;
    private final MinioStorageService minioStorageService;
    private final MinIOProperties minIOProperties;
    private final ElasticsearchChunkIndexRepository esChunkIndexRepository;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbVectorIndexMapper kbVectorIndexMapper;
    private final DocumentTagMapper documentTagMapper;




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

        List<KbDocument> pendingDocs = kbDocumentService.queryUnparsedDocuments();

        if (pendingDocs.isEmpty()) {
            log.info("没有待处理的文档");
            return;
        }

        List<Long> failedDocs = new ArrayList<>();
        for (KbDocument doc : pendingDocs) {
            try {
                parseKbDocumentList(doc);
            } catch (Exception e) {
                failedDocs.add(doc.getId());
                log.error("文档处理失败 {}: {}", doc.getId(), e.getMessage(), e);
            }
        }

        log.info("处理完成，总数: {}, 失败: {}", pendingDocs.size(), failedDocs.size());
    }


    public void parseKbDocumentList(KbDocument doc) {
        Long documentId = doc.getId();
        String fileName = doc.getFileName();
        String bucketName = minIOProperties.getBucket();
        String originalObjectKey = PathUtil.buildOriginalObjectKey(documentId, fileName);
        String textObjectKey = PathUtil.buildTextObjectKey(documentId);

        try {
            // 1下载原文件
            InputStream file = minioStorageService.downloadFile(originalObjectKey);

            // 2 Tika 解析
            String text = tikaDocumentParser.extractText(file, bucketName);

            // 3 上传解析文本
            minioStorageService.uploadText(textObjectKey, text);

            // 4 更新文档状态 & 路径
            doc.setParsedObjectKey(textObjectKey);
            // 已解析
            doc.setStatus(1);
            kbDocumentMapper.updateById(doc);

            // 5 分片（幂等，已分片的跳过）
            int chunkCount = kbChunkingService.chunkDocument(documentId);
            if (chunkCount == 0) {
                throw new RuntimeException("分片失败或未生成 chunk");
            }

            // 6向量化（幂等）
            kbEmbeddingService.embedDocument(documentId);

            // 7 更新最终状态
            // 已向量化
            doc.setStatus(2);
            kbDocumentMapper.updateById(doc);

        } catch (Exception e) {
            // 出现任何异常标记 9
            doc.setStatus(9);
            kbDocumentMapper.updateById(doc);
            throw new RuntimeException("文档处理失败"+e);
        }
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
            List<KbDocument> failedDocs = kbDocumentMapper.selectList(
                new LambdaQueryWrapper<KbDocument>()
                    .eq(KbDocument::getStatus, 9)
            );
            
            if (failedDocs.isEmpty()) {
                log.info("没有需要清理的解析错误文档");
                return;
            }
            
            log.info("发现 {} 个解析错误的文档，开始清理", failedDocs.size());
            int successCount = 0;
            int failCount = 0;
            
            for (KbDocument doc : failedDocs) {
                try {
                    cleanupFailedDocument(doc);
                    successCount++;
                    log.info("成功清理解析错误文档: {}", doc.getId());
                } catch (Exception e) {
                    failCount++;
                    log.error("清理解析错误文档失败: {}, 错误: {}", doc.getId(), e.getMessage(), e);
                }
            }
            
            log.info("清理完成，成功: {}, 失败: {}", successCount, failCount);
            
        } catch (Exception e) {
            log.error("清理解析错误文档过程中发生异常", e);
        }
        
        log.info("========== 清理解析错误的文档结束 ==========");
    }
    
    /**
     * 清理单个解析错误的文档
     *
     * @param doc 解析错误的文档
     */
    private void cleanupFailedDocument(KbDocument doc) {
        Long documentId = doc.getId();
        
        try {
            // 1. 从Elasticsearch中删除相关的chunks
            try {
                esChunkIndexRepository.deleteChunksByDocumentId(documentId);
                log.debug("已从ES中删除文档 {} 的chunks", documentId);
            } catch (Exception e) {
                log.warn("从ES中删除文档 {} 的chunks失败: {}", documentId, e.getMessage());
            }
            
            // 2. 从MinIO中删除文档相关文件
            try {
                minioStorageService.deleteDocumentFolder(documentId);
                log.debug("已从MinIO中删除文档 {} 的文件", documentId);
            } catch (Exception e) {
                log.warn("从MinIO中删除文档 {} 的文件失败: {}", documentId, e.getMessage());
            }
            
            // 3. 从数据库中删除相关记录
            // 查询该文档下的所有chunkId
            List<Long> chunkIds = kbDocumentChunkMapper.selectObjs(
                new LambdaQueryWrapper<KbDocumentChunk>()
                    .select(KbDocumentChunk::getId)
                    .eq(KbDocumentChunk::getDocumentId, documentId)
            );
            
            // 删除向量表记录
            if (!chunkIds.isEmpty()) {
                kbVectorIndexMapper.delete(
                    new LambdaQueryWrapper<KbVectorIndex>()
                        .in(KbVectorIndex::getChunkId, chunkIds)
                );
            }
            
            // 删除文档标签关联
            documentTagMapper.delete(
                new LambdaQueryWrapper<DocumentTag>()
                    .eq(DocumentTag::getDocumentId, documentId)
            );
            
            // 删除文档chunks
            kbDocumentChunkMapper.delete(
                new LambdaQueryWrapper<KbDocumentChunk>()
                    .eq(KbDocumentChunk::getDocumentId, documentId)
            );
            
            // 最后删除文档主记录
            kbDocumentMapper.deleteById(documentId);
            
            log.debug("已从数据库中删除文档 {} 的所有记录", documentId);
            
        } catch (Exception e) {
            log.error("清理文档 {} 时发生异常", documentId, e);
            throw e;
        }
    }

}
