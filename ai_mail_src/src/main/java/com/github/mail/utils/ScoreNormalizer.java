package com.github.mail.utils;

import java.util.List;


/**
 * 分数归一化工具类
 * <p>
 * 用于将不同量纲的分数归一化到 [0, 1] 区间,便于加权融合
 * 
 * @author Aster
 * @date 2025/12/30
 */
public class ScoreNormalizer {
    
    /**
     * Min-Max 归一化
     * <p>
     * 将分数从 [min, max] 映射到 [0, 1]
     * 
     * @param score 原始分数
     * @param min   最小值
     * @param max   最大值
     * @return 归一化后的分数 [0, 1]
     */
    public static double minMaxNormalize(double score, double min, double max) {
        if (max == min) {
            // 所有分数相同时返回 1.0
            return 1.0;
        }
        double normalized = (score - min) / (max - min);
        // 保证在 [0, 1]
        return Math.max(0.0, Math.min(1.0, normalized));
    }
    
    /**
     * 余弦相似度归一化
     * <p>
     * 将余弦相似度从 [-1, 1] 映射到 [0, 1]
     * 
     * @param cosineSimilarity 余弦相似度 [-1, 1]
     * @return 归一化后的分数 [0, 1]
     */
    public static double normalizeCosineSimilarity(double cosineSimilarity) {
        return (cosineSimilarity + 1.0) / 2.0;
    }
    
    /**
     * 批量 Min-Max 归一化
     * <p>
     * 自动计算 min 和 max,对整个列表归一化
     * 
     * @param scores 原始分数列表
     * @return 归一化后的分数列表
     */
    public static List<Double> batchMinMaxNormalize(List<Double> scores) {
        if (scores.isEmpty()) {
            return scores;
        }
        
        double min = scores.stream().min(Double::compare).orElse(0.0);
        double max = scores.stream().max(Double::compare).orElse(1.0);
        
        return scores.stream()
                .map(score -> minMaxNormalize(score, min, max))
                .toList();
    }
}
