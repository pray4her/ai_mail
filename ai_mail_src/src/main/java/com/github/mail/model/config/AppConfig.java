package com.github.mail.model.config;

import lombok.Data;

/**
 * 应用总配置类，用于动态配置读取
 * 纯POJO，不使用Spring注解
 * 
 * @author System
 * @date 2026/01/06
 */
@Data
public class AppConfig {

    
    /**
     * 邮件配置
     */
    private MailConfig mail = new MailConfig();

    
    /**
     * DeepSeek配置 兼容其它OPENAi格式模型
     */
    private DeepSeekConfig deepseek = new DeepSeekConfig();
    
    /**
     * Embedding配置
     */
    private EmbeddingConfig embedding = new EmbeddingConfig();

}