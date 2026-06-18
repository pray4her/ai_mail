package com.github.mail.utils;

import com.github.mail.model.config.Properties.MailServerProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.imap.IMAPStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 邮箱连接工具类
 * @author Aster
 * @date 2025/12/25
 */

@Slf4j
public class MailConnectUtil {

    /**
     * 获取 IMAP Store 并连接
     */
    public static Store connect(MailServerProperties.Imap imapConfig) throws MessagingException {
        // 检查配置安全性
        MailSecurityUtil.isSecureConfig(imapConfig);
        
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap");
        props.put("mail.imap.host", imapConfig.getHost());
        props.put("mail.imap.port", imapConfig.getPort());
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.auth", "true");
        // 对于 993 隐式 SSL，关闭 STARTTLS 以避免协商歧义
        props.put("mail.imap.starttls.enable", "false");
        // 明确禁用 PLAIN，避免触发 “Not support PLAIN!”
        props.put("mail.imap.auth.plain.disable", "true");
        // 使用更通用的 LOGIN 机制，兼容 QQ 等邮箱的授权码登录
        props.put("mail.imap.auth.mechanisms", "LOGIN");
        props.put("mail.imap.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.imap.ssl.trust", "*");
        props.put("mail.imap.idenable", "true");
        // 设置连接超时时间
        props.put("mail.imap.connectiontimeout", "10000");
        // 设置读取超时时间
        props.put("mail.imap.timeout", "10000");
        // 设置socket超时时间
        props.put("mail.imap.socketFactory.fallback", "false");

        // 针对网易可能需要的额外属性
        props.put("mail.imap.partialfetch", "false");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect(imapConfig.getHost(), imapConfig.getPort(), imapConfig.getUserName(), imapConfig.getPassword());

        if (store instanceof IMAPStore imapStore) {
            Map<String, String> clientParams = new HashMap<>();
            clientParams.put("name", "Foxmail");
            clientParams.put("version", "7.2.25.158");
            clientParams.put("vendor", "TencentMe");
            // 有些情况下需要传这个参数
            clientParams.put("policy", "default");
            imapStore.id(clientParams);
        }

        return store;
    }

}