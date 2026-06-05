package com.github.mail.model.config.Properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * minio配置（yml读取）
 * @author Aster
 * @date 2026/1/6
 */

@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinIOProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;

}
