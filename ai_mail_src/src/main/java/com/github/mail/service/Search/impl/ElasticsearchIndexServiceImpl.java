package com.github.mail.service.Search.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.github.mail.model.config.Properties.ElasticSearchProperties;
import com.github.mail.service.Search.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 索引管理服务实现
 * 
 * @author Aster
 * @date 2025/12/30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexServiceImpl implements ElasticsearchIndexService {
    
    private final ElasticsearchClient esClient;

    private final ElasticSearchProperties esProperties;

    @Override
    public boolean createKbChunksIndex() {
        String indexName = esProperties.getKbChunksIndex();
        try {
            // 检查索引是否已存在
            if (indexExists(indexName)) {
                log.info("索引已存在: {}", indexName);
                return true;
            }
            
            log.info("开始创建索引: {}", indexName);
            
            // 构建 Mapping
            TypeMapping mapping = TypeMapping.of(m -> m
                    .properties("chunk_id", Property.of(p -> p
                            .long_(LongNumberProperty.of(l -> l))))
                    .properties("document_id", Property.of(p -> p
                            .long_(LongNumberProperty.of(l -> l))))
                    .properties("chunk_index", Property.of(p -> p
                            .integer(IntegerNumberProperty.of(i -> i))))
                    .properties("text_content", Property.of(p -> p
                            .text(TextProperty.of(t -> t
                                    .analyzer("ik_max_word")
                                    .searchAnalyzer("ik_smart")
                            ))))
                    .properties("text_vector", Property.of(p -> p
                            .denseVector(DenseVectorProperty.of(d -> d
                                    .dims(esProperties.getDimension())
                                    .index(true)
                                    .similarity("cosine")))))
                    .properties("token_count", Property.of(p -> p
                            .integer(IntegerNumberProperty.of(i -> i))))
                    .properties("created_at", Property.of(p -> p
                            .long_(LongNumberProperty.of(l -> l))))
            );
            
            // 创建索引
            CreateIndexRequest request = CreateIndexRequest.of(c -> c
                    .index(indexName)
                    //设置节约内存
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                    )
                    .mappings(mapping)
            );
            
            esClient.indices().create(request);
            log.info("索引创建成功: {}", indexName);
            return true;
            
        } catch (Exception e) {
            log.error("创建索引失败: {}", indexName, e);
            return false;
        }
    }
    
    @Override
    public boolean indexExists(String indexName) {
        try {
            ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
            return esClient.indices().exists(request).value();
        } catch (Exception e) {
            log.error("检查索引是否存在失败: {}", indexName, e);
            return false;
        }
    }
    
    @Override
    public boolean deleteIndex(String indexName) {
        try {
            if (!indexExists(indexName)) {
                log.warn("索引不存在,无需删除: {}", indexName);
                return true;
            }
            
            DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
            esClient.indices().delete(request);
            log.info("索引删除成功: {}", indexName);
            return true;
            
        } catch (Exception e) {
            log.error("删除索引失败: {}", indexName, e);
            return false;
        }
    }
}

