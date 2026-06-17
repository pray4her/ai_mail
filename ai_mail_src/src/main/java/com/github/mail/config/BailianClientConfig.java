package com.github.mail.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.github.mail.model.config.Properties.BailianProperties;

/**
 * 百炼 SDK 相关 Spring 配置
 *
 * @author System
 */
@Configuration
@EnableConfigurationProperties(BailianProperties.class)
public class BailianClientConfig {
}
