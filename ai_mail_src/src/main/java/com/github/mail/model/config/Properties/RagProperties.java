package com.github.mail.model.config.Properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 *
 * rag检索策略配置（（yml读取））
 * @author Aster
 * @date 2026/1/6
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.hybrid")
public class RagProperties {

    /**
     *     use-elasticsearch: true  # 是否使用 ES (启用后优先使用 ES 检索)
     *     vector-recall-size: 300  # 向量召回候选集大小
     *     bm25-weight: 1.0         # BM25 分数权重 (关键词主导)
     *     vector-weight: 0.2       # 向量分数权重 (语义微调)
     *     min-score: 0.5           # 最终融合分数阈值
     *     default-topk: 5          # 默认返回数量
     *     default-min-score: 0.3
     */

    //是否启用es，目前系统全面迁移到ES
    private boolean useElasticsearch = true;
    //向量召回候选集大小
    private int vectorRecallSize = 300;
    //BM25分数权重 (关键词主导)
    private double bm25Weight = 1.0;
    //向量分数权重 (语义微调)
    private double vectorWeight = 0.2;
    //最终融合分数阈值
    private double minScore = 0.25;
    //默认返回数量
    private int defaultTopk = 5;
    //默认最小分数阈值
    private double defaultMinScore = 0.25;


}