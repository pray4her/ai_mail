package com.github.mail.model.config;

import lombok.Data;

/**
 * Embedding配置类（动态配置读取）
 * 纯POJO，不使用Spring注解
 * 
 * @author System
 * @date 2026/01/06
 */
@Data
public class EmbeddingConfig {
    
    /**
     * 阿里云Embedding配置
     */
    private Ali ali = new Ali();
    
    /**
     * 阿里云Embedding配置内部类
     */
    @Data
    public static class Ali {
        /**
         * API密钥
         */
        private String apiKey = "";
        
        /**
         * API URL
         */
        private String apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        
        /**
         * 模型名称
         */
        private String model = "text-embedding-v4";
        
        /**
         * 批处理大小
         */
        private int batchSize = 10;
        
        /**
         * 向量维度
         */
        private int dimension = 2048;
    }
}