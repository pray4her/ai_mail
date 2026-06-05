package com.github.mail.model.config.Properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ES搜索配置（yml读取）
 * @author Aster
 * @date 2026/1/6
 */

@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch.search")
public class ElasticSearchProperties {

    // 知识库分片索引
    private String kbChunksIndex = "kb_chunks";
    // 向量维度
    private Integer dimension = 2048;

}
