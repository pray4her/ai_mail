package com.github.mail.model.config.Properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 百炼 SDK 静态配置（application.yml）
 *
 * @author System
 */
@Data
@ConfigurationProperties(prefix = "app.rag.bailian")
public class BailianProperties {

    private String accessKeyId = "";

    private String accessKeySecret = "";

    private String workspaceId = "";

    private String indexId = "";

    private String endpoint = "bailian.cn-beijing.aliyuncs.com";

    private int connectTimeoutMs = 30000;

    private int readTimeoutMs = 60000;
}
