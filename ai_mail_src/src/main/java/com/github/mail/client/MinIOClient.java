package com.github.mail.client;

import com.github.mail.model.config.Properties.MinIOProperties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * minIO存储客户端
 * @author Aster
 * @date 2025/12/26
 */

@Configuration
public class MinIOClient {

    private final MinIOProperties minioProperties;


    MinIOClient(MinIOProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
