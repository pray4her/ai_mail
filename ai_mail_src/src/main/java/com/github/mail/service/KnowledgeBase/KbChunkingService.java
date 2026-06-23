package com.github.mail.service.KnowledgeBase;

import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.service.File.MinioStorageService;
import com.github.mail.utils.ChineseSemanticChunker;
import com.github.mail.utils.ChineseSemanticChunker.ChunkResult;
import com.github.mail.utils.PathUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 知识库分片服务
 * <p>
 * 职责：
 * 1. 对知识库文档进行分片
 * 2. 使用贪心算法作为默认分片策略
 * 3. 事务性插入分片记录
 * 4. 为后续的向量化做准备
 * <p>
 * 设计原则：
 * - 只处理知识库文档（kb_document），不处理邮件
 * - 元数据完整：保留足够的信息便于溯源和重构
 * - 事务保护：分片插入失败则回滚
 * - 异步处理：分片任务可独立调度
 *
 * @author Asteries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbChunkingService {

    private final KbDocumentChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;

    private final ChineseSemanticChunker chunker = new ChineseSemanticChunker(512, 100,400);
    private final MinioStorageService storageService;


    /**
     * 对知识库文档进行分片处理
     *
     * @param documentId 文档 ID
     * @return 分片数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int chunkDocument(Long documentId) {
        // 查询文档
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("Document not found: {}", documentId);
            return 0;
        }

        if (document.getStatus() != 1) {
            log.warn("Document not parsed yet: {}, status: {}", documentId, document.getStatus());
            return 0;
        }

        List<KbDocumentChunk> existingChunks = chunkMapper.selectByDocumentId(documentId);
        if (!existingChunks.isEmpty()) {
            log.info("Document already chunked: id={}, chunks={}", documentId, existingChunks.size());
            return existingChunks.size();
        }

        String text = loadDocumentText(document);

        if (text == null || text.isEmpty()) {
            log.warn("Document has no text content: {}", documentId);
            return 0;
        }

        // 执行分片
        List<ChunkResult> chunks = split(text);

        // 插入分片记录
        for (int i = 0; i < chunks.size(); i++) {
            ChunkResult chunkResult = chunks.get(i);

            KbDocumentChunk chunk = new KbDocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(i);
            chunk.setChunkMd5(calculateMd5(chunkResult.getContent()));
            chunk.setTextContent(chunkResult.getContent());
            chunk.setTokenCount(estimateTokens(chunkResult.getContent()));
            chunk.setCreatedAt(LocalDateTime.now());

            chunkMapper.insert(chunk);
        }

        log.info("Chunked document {} into {} chunks ()",
                documentId, chunks.size());

        return chunks.size();
    }

    /**
     * 批量分片处理
     *
     * @param documentIds 文档 ID 列表
     * @return 总分片数
     */
    @Transactional(rollbackFor = Exception.class)
    public int chunkDocumentsBatch(List<Long> documentIds) {
        int totalChunks = 0;
        for (Long documentId : documentIds) {
            try {
                totalChunks += chunkDocument(documentId);
            } catch (Exception e) {
                log.error("Failed to chunk document {}", documentId, e);
            }
        }
        return totalChunks;
    }

    /**
     * 加载文档文本内容
     */
    private String loadDocumentText(KbDocument document) {
        String path = PathUtil.buildTextObjectKey(document.getId());
        return storageService.readText(path);
    }

    /**
     * 分片执行 此处调用实际的分片算法
     */
    private List<ChunkResult> split(String text) {
        return chunker.chunk(text);
    }


    /**
     * 估算 Token 数量（简化版）
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\u4E00' && c <= '\u9FFF') {
                chineseCount++;
            }
        }
        int spaceCount = (int) text.chars().filter(c -> c == ' ').count();
        int englishWords = spaceCount + 1;
        return chineseCount + (int) (englishWords * 1.3);
    }

    /**
     * 计算 MD5
     */
    private String calculateMd5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to calculate MD5", e);
            return "";
        }
    }


}
