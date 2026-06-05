package com.github.mail.model.config;

import lombok.Data;

/**
 * DeepSeek配置类（动态配置读取）
 * 纯POJO，不使用Spring注解
 * 
 * @author System
 * @date 2026/01/06
 */
@Data
public class DeepSeekConfig {
    
    /**
     * API密钥
     */
    private String apiKey = "";
    
    /**
     * API URL
     */
    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";
    
    /**
     * 模型名称
     */
    private String model = "deepseek-chat";
}