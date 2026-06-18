package com.github.mail.model.config.Properties;

import com.github.mail.model.config.MailConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮件服务器配置实体类
 * @author Asteries 作为映射保留
 */
@Data
public class MailServerProperties {

    private Imap imap = new Imap();
    //TODO：smtp用于发送邮件，系统未使用
    private Smtp smtp = new Smtp();


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Imap {
        private String host;
        private Integer port;
        private String userName;
        private String password;
        private boolean ssl;
    }

    @Data
    public static class Smtp {
        private String host;
        private Integer port;
        private String userName;
        private String password;
    }

    public static MailServerProperties.Imap fromMailConfig(MailConfig.Imap mailConfig) {
        MailServerProperties.Imap mailServerProperties = new MailServerProperties.Imap();
        mailServerProperties.setHost(mailConfig.getHost());
        mailServerProperties.setPort(mailConfig.getPort());
        mailServerProperties.setUserName(mailConfig.getUsername());
        mailServerProperties.setPassword(mailConfig.getPassword());
        mailServerProperties.setSsl(mailConfig.isSsl());
        return mailServerProperties;
    }

}
