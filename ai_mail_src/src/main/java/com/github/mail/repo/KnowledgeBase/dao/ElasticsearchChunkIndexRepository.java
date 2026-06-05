package com.github.mail.repo.KnowledgeBase.dao;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.github.mail.model.config.Properties.ElasticSearchProperties;
import com.github.mail.repo.KnowledgeBase.domain.EsKbChunk;
import com.github.mail.repo.KnowledgeBase.domain.KbDocumentChunk;
import com.github.mail.repo.KnowledgeBase.mapper.EsKbChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch 向量持久化服务
 * <p>
 * 职责：
 * 1. 封装 ES 的 CRUD 操作
 * 2. 管理知识库 chunk 的索引
 * 3. 支持单条/批量操作
 * <p>
 * 设计原则：
 * - 只负责 ES 数据操作，不涉及业务逻辑
 * - 统一异常处理和日志记录
 * - 提供批量操作提高性能
 *
 * @author Aster
 * @date 2025/12/31
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ElasticsearchChunkIndexRepository {

    private final ElasticsearchClient esClient;
    private final EsKbChunkMapper esKbChunkMapper;
    private final ElasticSearchProperties elasticsearchProperties;


    /**
     * 保存单个 chunk 到 ES
     *
     * @param chunk        文档分块
     * @param vectorArray  向量数组
     * @return 是否成功
     */
    public boolean saveChunk(KbDocumentChunk chunk, float[] vectorArray) {
        try {
            EsKbChunk esKbChunk = esKbChunkMapper.toEsChunk(chunk, vectorArray);

            esClient.index(i -> i
                    .index(elasticsearchProperties.getKbChunksIndex())
                    .id(String.valueOf(chunk.getId()))
                    .document(esKbChunk)
            );

            // 索引到 ES
            IndexResponse response = esClient.index(i -> i
                    .index(elasticsearchProperties.getKbChunksIndex())
                    .id(String.valueOf(chunk.getId()))
                    .document(esKbChunk)
            );

            log.debug("保存 chunk 到 ES 成功: chunk_id={}, result={}", chunk.getId(), response.result());
            return true;

        } catch (IOException e) {
            log.error("保存 chunk 到 ES 失败: chunk_id={}", chunk.getId(), e);
            return false;
        }
    }

    /**
     * 批量保存 chunks 到 ES
     *
     * @param chunks      文档分块列表
     * @param vectors     向量数组列表
     * @return 成功保存的数量
     */
    public int batchSaveChunks(List<KbDocumentChunk> chunks, List<float[]> vectors) {
        if (chunks.isEmpty() || vectors.isEmpty() || chunks.size() != vectors.size()) {
            log.warn("批量保存参数无效: chunks.size={}, vectors.size={}", chunks.size(), vectors.size());
            return 0;
        }

        try {
            log.info("开始批量保存 chunks 到 ES: 数量={}", chunks.size());

            // 构建 bulk 操作
            List<BulkOperation> operations = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                KbDocumentChunk chunk = chunks.get(i);
                float[] vectorArray = vectors.get(i);

                EsKbChunk esKbChunk = esKbChunkMapper.toEsChunk(chunk, vectorArray);

                final Long chunkId = chunk.getId();
                operations.add(BulkOperation.of(op -> op
                        .index(idx -> idx
                                .index(elasticsearchProperties.getKbChunksIndex())
                                .id(String.valueOf(chunkId))
                                .document(esKbChunk)
                        )
                ));
            }

            // 执行 bulk 请求
            BulkResponse response = esClient.bulk(BulkRequest.of(b -> b
                    .operations(operations)
            ));

            // 统计成功数量
            int successCount = (int) response.items().stream()
                    .filter(item -> item.error() == null)
                    .count();

            log.info("批量保存 chunks 完成: 成功={}, 失败={}", successCount, chunks.size() - successCount);
            return successCount;

        } catch (IOException e) {
            log.error("批量保存 chunks 失败", e);
            return 0;
        }
    }

    //TODO：删除方式未使用单个chunk删除，使用文档id批量删除，后续可扩展
    /**
     * 根据 chunk_id 删除
     *
     * @param chunkId chunk ID
     * @return 是否成功
     */
    public boolean deleteChunk(Long chunkId) {
        try {
            DeleteResponse response = esClient.delete(d -> d
                    .index(elasticsearchProperties.getKbChunksIndex())
                    .id(String.valueOf(chunkId))
            );

            log.debug("删除 chunk 成功: chunk_id={}, result={}", chunkId, response.result());
            return true;

        } catch (IOException e) {
            log.error("删除 chunk 失败: chunk_id={}", chunkId, e);
            return false;
        }
    }

    /**
     * 批量删除 chunks
     *
     * @param chunkIds chunk ID 列表
     * @return 成功删除的数量
     */
    public int batchDeleteChunks(List<Long> chunkIds) {
        if (chunkIds.isEmpty()) {
            log.warn("批量删除参数为空");
            return 0;
        }

        try {
            log.info("开始批量删除 chunks: 数量={}", chunkIds.size());

            // 构建 bulk 删除操作
            List<BulkOperation> operations = chunkIds.stream()
                    .map(chunkId -> BulkOperation.of(op -> op
                            .delete(d -> d
                                    .index(elasticsearchProperties.getKbChunksIndex())
                                    .id(String.valueOf(chunkId))
                            )
                    ))
                    .collect(Collectors.toList());

            // 执行 bulk 请求
            BulkResponse response = esClient.bulk(BulkRequest.of(b -> b
                    .operations(operations)
            ));

            // 统计成功数量
            int successCount = (int) response.items().stream()
                    .filter(item -> item.error() == null)
                    .count();

            log.info("批量删除 chunks 完成: 成功={}, 失败={}", successCount, chunkIds.size() - successCount);
            return successCount;

        } catch (IOException e) {
            log.error("批量删除 chunks 失败", e);
            return 0;
        }
    }

    /**
     * 根据文档 ID 删除所有 chunks
     *
     * @param documentId 文档 ID
     * @return 删除的数量
     */
    public int deleteChunksByDocumentId(Long documentId) {
        try {
            log.info("开始删除文档的所有 chunks: document_id={}", documentId);

            // 使用 delete by query
            DeleteByQueryResponse response = esClient.deleteByQuery(d -> d
                    .index(elasticsearchProperties.getKbChunksIndex())
                    .query(q -> q
                            .term(t -> t
                                    .field("document_id")
                                    .value(documentId)
                            )
                    )
            );

            int deletedCount = response.deleted().intValue();
            log.info("删除文档 chunks 完成: document_id={}, deleted={}", documentId, deletedCount);
            return deletedCount;

        } catch (IOException e) {
            log.error("删除文档 chunks 失败: document_id={}", documentId, e);
            return 0;
        }
    }

    /**
     * 更新 chunk（先删后增）
     *
     * @param chunk       文档分块
     * @param vectorArray 向量数组
     * @return 是否成功
     */
    public boolean updateChunk(KbDocumentChunk chunk, float[] vectorArray) {
        // ES 的 index 操作本身就是 upsert（存在则更新，不存在则创建）
        return saveChunk(chunk, vectorArray);
    }

    /**
     * 检查 chunk 是否存在
     *
     * @param chunkId chunk ID
     * @return 是否存在
     */
    public boolean chunkExists(Long chunkId) {
        try {
            GetResponse<EsKbChunk> response = esClient.get(g -> g
                            .index(elasticsearchProperties.getKbChunksIndex())
                            .id(String.valueOf(chunkId)),
                    EsKbChunk.class
            );

            return response.found();

        } catch (IOException e) {
            log.error("检查 chunk 是否存在失败: chunk_id={}", chunkId, e);
            return false;
        }
    }
}