package com.github.mail.utils;

import com.github.mail.model.config.Properties.MailServerProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 邮件安全连接工具类
 * 提供安全连接相关的工具方法
 *
 * @author Aster
 * @date 2025/12/25
 */
@Slf4j
public class MailSecurityUtil {

    /**
     * 验证邮件配置是否安全
     * @param config 邮件服务器配置
     * @return 是否安全
     */
    public static boolean isSecureConfig(MailServerProperties.Imap config) {
        // 检查是否使用了SSL
        if (!config.isSsl()) {
            log.warn("Warning: SSL is not enabled for IMAP connection. This is insecure.");
            return false;
        }

        // 检查是否使用了安全端口
        if (config.getPort() != 993 && config.getPort() != 465) {
            log.warn("Warning: Non-standard secure port ({}) is being used. Recommended: 993 for IMAP.", config.getPort());
        }

        // 检查是否使用了安全协议
        String host = config.getHost();
        if (host != null) {
            if (host.contains("163.com") || host.contains("188.com") || host.contains("qq.com")) {
                log.info("Detected Chinese email provider: {}. Please ensure you're using app-specific password instead of login password.", host);
            }
        }

        return true;
    }

    /**
     * 获取安全连接建议
     * @param config 邮件服务器配置
     * @return 安全建议
     */
    public static String getSecurityRecommendation(MailServerProperties.Imap config) {
        StringBuilder recommendation = new StringBuilder();
        recommendation.append("Security recommendations for your email provider:\n");

        String host = config.getHost();
        if (host != null) {
            if (host.contains("163.com")) {
                recommendation.append("- For 163 email: Enable IMAP/SMTP service in your email settings\n");
                recommendation.append("- Generate an app-specific password instead of using your login password\n");
                recommendation.append("- Use the app password in your configuration\n");
            } else if (host.contains("188.com")) {
                recommendation.append("- For 188 email: Enable IMAP/SMTP service in your email settings\n");
                recommendation.append("- Generate an app-specific password instead of using your login password\n");
                recommendation.append("- Use the app password in your configuration\n");
            } else if (host.contains("qq.com")) {
                recommendation.append("- For QQ email: Enable IMAP/SMTP service in your email settings\n");
                recommendation.append("- Generate an app-specific password instead of using your login password\n");
                recommendation.append("- Use the app password in your configuration\n");
            } else if (host.contains("gmail.com")) {
                recommendation.append("- For Gmail: Enable 2-factor authentication\n");
                recommendation.append("- Generate an app-specific password instead of using your login password\n");
                recommendation.append("- Use the app password in your configuration\n");
            } else {
                recommendation.append("- Ensure SSL/TLS is enabled\n");
                recommendation.append("- Use app-specific password if your provider supports it\n");
                recommendation.append("- Check that your firewall allows the connection\n");
            }
        }

        return recommendation.toString();
    }
}