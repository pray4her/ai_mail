package com.github.mail.service.Search;

/**
 * Elasticsearch 索引管理服务
 * <p>
 * 负责索引的创建、删除、检查等操作
 * 
 * @author Aster
 * @date 2025/12/30
 */
public interface ElasticsearchIndexService {
    
    /**
     * 创建知识库索引 (kb_chunks)
     * <p>
     * Index Mapping:
     * - chunk_id: long (唯一标识)
     * - document_id: long
     * - chunk_index: integer
     * - text_content: text (用于 BM25)
     * - text_vector: dense_vector (用于 knn)
     * - token_count: integer
     * - created_at: long (时间戳)
     * 
     * @return 是否创建成功
     */
    boolean createKbChunksIndex();
    
    /**
     * 检查索引是否存在
     * 
     * @param indexName 索引名称
     * @return 是否存在
     */
    boolean indexExists(String indexName);
    
    /**
     * 删除索引
     * 
     * @param indexName 索引名称
     * @return 是否删除成功
     */
    boolean deleteIndex(String indexName);
}
