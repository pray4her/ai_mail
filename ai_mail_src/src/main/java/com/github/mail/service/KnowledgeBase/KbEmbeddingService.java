package com.github.mail.service.KnowledgeBase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mail.client.EmbeddingClient;
import com.github.mail.repo.KbDocument.domain.KbDocument;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.domain.KbVectorIndex;
import com.github.mail.repo.KnowledgeBase.mapper.KbDocumentChunkMapper;
import com.github.mail.repo.KbDocument.mapper.KbDocumentMapper;
import com.github.mail.repo.KnowledgeBase.mapper.KbVectorIndexMapper;
import com.github.mail.repo.KnowledgeBase.dao.ElasticsearchChunkIndexRepository;
import com.github.mail.service.Config.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库向量化服务
 * <p>
 * 职责：
 * 1. 调用 embedding API（如 OpenAI/DeepSeek/阿里云）
 * 2. 将 chunk 向量化
 * 3. 存入 Elasticsearch 向量库
 * 4. 记录 chunk 与向量的映射关系到 MySQL
 * <p>
 * 设计原则：
 * - 只对知识库 chunk 进行向量化，不对邮件向量化
 * - 向量存储在 Elasticsearch
 * - MySQL 存储映射关系（kb_vector_index）
 * - 事务保护：chunk + vector_index + ES 原子性
 *
 * @author Asteries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbEmbeddingService {

    private final KbDocumentChunkMapper chunkMapper;
    private final KbVectorIndexMapper vectorIndexMapper;
    private final KbDocumentMapper documentMapper;
    private final EmbeddingClient embeddingClient;
    private final ElasticsearchChunkIndexRepository esVectorService;
    private final ObjectMapper objectMapper;

    private final ConfigService configService;

    /**
     * 获取当前使用的 embedding 模型
     */
    private String getCurrentModel() {
        return configService.getConfig().getEmbedding().getAli().getModel();
    }

    /**
     * 对文档的所有 chunk 进行向量化
     *
     * @param documentId 文档ID
     * @return 成功向量化的 chunk 数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int embedDocument(Long documentId) {
        // 查询文档
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("Document not found: {}", documentId);
            return 0;
        }

        if (document.getStatus() != 1) {
            log.warn("Document not ready for embedding: {}, status: {}", documentId, document.getStatus());
            return 0;
        }

        // 查询所有 chunk
        List<KbDocumentChunk> chunks = chunkMapper.selectByDocumentId(documentId);
        if (chunks.isEmpty()) {
            log.warn("No chunks found for document: {}", documentId);
            return 0;
        }

        log.info("开始向量化文档: document_id={}, chunks={}", documentId, chunks.size());



        //过滤已有向量 TODO：之后可以优化为状态表
        List<Long> chunkIds = chunks.stream().map(KbDocumentChunk::getId).toList();
        List<KbVectorIndex> existing = vectorIndexMapper.selectList(
                new LambdaQueryWrapper<KbVectorIndex>()
                        .in(KbVectorIndex::getChunkId, chunkIds)
                        .eq(KbVectorIndex::getModelVersion, getCurrentModel())
        );
        Set<Long> existingIds = existing.stream().map(KbVectorIndex::getChunkId).collect(Collectors.toSet());
        List<KbDocumentChunk> pendingChunks = chunks.stream()
                .filter(c -> !existingIds.contains(c.getId()))
                .toList();
        if (pendingChunks.isEmpty()) {
            return 0;
        }

        int successCount = batchEmbedChunks(pendingChunks);

        // 更新文档状态为"已向量化"
        if (successCount == pendingChunks.size()) {
            document.setStatus(2);
            document.setVectorizedAt(LocalDateTime.now());
            documentMapper.updateById(document);
            log.info("Document fully vectorized: id={}, chunks={}", documentId, successCount);
        } else {
            log.warn("Partial vectorization: id={}, success={}/{}",
                    documentId, successCount, pendingChunks.size());
        }

        return successCount;
    }


    /**
     * 批量处理待向量化的 chunk
     *
     * @return 处理的数量
     */
    public int batchEmbedChunks(List<KbDocumentChunk> pendingChunks) {

        List<String> chunkContent = pendingChunks.stream()
                .map(KbDocumentChunk::getTextContent)
                .toList();

        List<float[]> vectors = embeddingClient.embedBatch(chunkContent);

        if (vectors == null || vectors.isEmpty() || vectors.size() != chunkContent.size()) {
            throw new RuntimeException("Embedding result is empty for chunk: ");
        }

        //存es
        int esSaved = esVectorService.batchSaveChunks(pendingChunks, vectors);

        if (esSaved != pendingChunks.size()) {
            throw new RuntimeException("Failed to save chunk to Elasticsearch: ");
        }

        //存mysql
        List<KbVectorIndex> vectorIndexes = new ArrayList<>();
        // 一次性时间戳
        long timestamp = System.currentTimeMillis();
        for (KbDocumentChunk chunk : pendingChunks) {
            KbVectorIndex vectorIndex = new KbVectorIndex();
            vectorIndex.setChunkId(chunk.getId());
            vectorIndex.setEmbeddingId("vec_" + chunk.getId() + "_" + timestamp);
            vectorIndex.setModelVersion(getCurrentModel());
            vectorIndex.setCreatedAt(LocalDateTime.now());

            vectorIndexes.add(vectorIndex);
        }
        vectorIndexMapper.insert(vectorIndexes);
        return pendingChunks.size();
    }

    /**
     * 对单个 chunk 进行向量化并存入 ES //TODO:目前架构并未启用
     * <p>
     * 流程：
     * 1. 检查是否已向量化
     * 2. 调用 embedding API 获取向量
     * 3. 存入 Elasticsearch
     * 4. 记录映射关系到 MySQL
     */
    @Transactional(rollbackFor = Exception.class)
    public void embedChunk(KbDocumentChunk chunk) {
        // 检查是否已经向量化
        Long existingCount = vectorIndexMapper.selectCount(
                new LambdaQueryWrapper<KbVectorIndex>()
                        .eq(KbVectorIndex::getChunkId, chunk.getId())
                        .eq(KbVectorIndex::getModelVersion, getCurrentModel())
        );

        if (existingCount != null && existingCount > 0) {
            log.debug("Chunk already vectorized: {}", chunk.getId());
            return;
        }

        // 第一步：调用 embedding API
        float[] embedding = embeddingClient.embed(chunk.getTextContent());

        if (embedding == null || embedding.length == 0) {
            throw new RuntimeException("Embedding result is empty for chunk: " + chunk.getId());
        }

        // 第二步：存入 Elasticsearch
        boolean esSaved = esVectorService.saveChunk(chunk, embedding);
        if (!esSaved) {
            throw new RuntimeException("Failed to save chunk to Elasticsearch: " + chunk.getId());
        }

        // 第三步：记录映射关系到 MySQL
        // 生成 embedding_id（使用 chunk_id 作为标识）
        String embeddingId = "vec_" + chunk.getId() + "_" + System.currentTimeMillis();

        KbVectorIndex vectorIndex = new KbVectorIndex();
        vectorIndex.setChunkId(chunk.getId());
        vectorIndex.setEmbeddingId(embeddingId);
        vectorIndex.setModelVersion(getCurrentModel());
        vectorIndex.setCreatedAt(LocalDateTime.now());

        //目前存mysql数据库了
        try {
            String vectorJson = objectMapper.writeValueAsString(embedding);
            vectorIndex.setEmbeddingVector(vectorJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        vectorIndexMapper.insert(vectorIndex);

        log.info("Chunk vectorized and saved: chunk_id={}, embedding_id={}, dimension={}",
                chunk.getId(), embeddingId, embedding.length);
    }



    /**
     * 批量处理待向量化的 chunk //TODO：保留，但暂时不会出现待向量化的chunk
     *
     * @param limit 本次处理的最大数量
     * @return 处理的数量
     */
    public int embedPendingChunks(int limit) {
        List<KbDocumentChunk> pendingChunks = chunkMapper.selectPendingChunks(getCurrentModel(), limit);

        if (pendingChunks.isEmpty()) {
            log.debug("No pending chunks to embed");
            return 0;
        }

        log.info("开始批量向量化 pending chunks: 数量={}", pendingChunks.size());

        int successCount = 0;
        successCount = batchEmbedChunks(pendingChunks);

        log.info("批量向量化完成: 成功={}, 失败={}", successCount, pendingChunks.size() - successCount);
        return successCount;
    }

    /**
     * 删除文档的所有向量数据
     * <p>
     * 同时删除 ES 和 MySQL 的数据
     *
     * @param documentId 文档ID
     * @return 删除的 chunk 数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteDocumentVectors(Long documentId) {
        // 查询文档的所有 chunk
        List<KbDocumentChunk> chunks = chunkMapper.selectByDocumentId(documentId);
        if (chunks.isEmpty()) {
            log.warn("No chunks found for document: {}", documentId);
            return 0;
        }

        log.info("开始删除文档向量: document_id={}, chunks={}", documentId, chunks.size());

        // 删除 ES 中的数据
        int esDeleted = esVectorService.deleteChunksByDocumentId(documentId);

        // 删除 MySQL 中的映射关系
        List<Long> chunkIds = chunks.stream()
                .map(KbDocumentChunk::getId)
                .toList();

        int mysqlDeleted = 0;
        for (Long chunkId : chunkIds) {
            mysqlDeleted += vectorIndexMapper.delete(
                    new LambdaQueryWrapper<KbVectorIndex>()
                            .eq(KbVectorIndex::getChunkId, chunkId)
            );
        }

        log.info("删除文档向量完成: document_id={}, ES删除={}, MySQL删除={}",
                documentId, esDeleted, mysqlDeleted);

        return esDeleted;
    }

    /**
     * 重新向量化文档 TODO：未启用，保留
     * <p>
     * 先删除旧数据，再重新向量化
     *
     * @param documentId 文档ID
     * @return 成功向量化的数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int reEmbedDocument(Long documentId) {
        log.info("开始重新向量化文档: document_id={}", documentId);

        // 删除旧向量
        deleteDocumentVectors(documentId);

        // 重置文档状态
        KbDocument document = documentMapper.selectById(documentId);
        if (document != null) {
            // 重置为待向量化状态
            document.setStatus(1);
            document.setVectorizedAt(null);
            documentMapper.updateById(document);
        }

        // 重新向量化
        return embedDocument(documentId);
    }
}
