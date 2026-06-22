package com.github.mail.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件配置类 （动态配置读取）
 * 纯POJO，不使用Spring注解
 * 
 * @author System
 * @date 2026/01/06
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.mail")
public class MailConfig {
    
    /**
     * 自动回复配置
     */
    private AutoReply autoReply = new AutoReply();

    /**
     * IMAP配置
     */
    private List<Imap> imapList = new ArrayList<>();
    
    /**
     * RAG配置
     */
    private Rag rag = new Rag();

    private HistorySync historySync = new HistorySync();
    
    /**
     * 自动回复配置内部类
     */
    @Data
    public static class AutoReply {
        
        /**
         * 间隔配置
         */
        private Interval interval = new Interval();
        
        /**
         * 阈值配置
         */
        private Threshold threshold = new Threshold();
        
        /**
         * 草稿文件夹
         */
        private String draftFolder = "AI_reply";
    }
    
    /**
     * 间隔配置内部类
     */
    @Data
    public static class Interval {
        /**
         * 级别1间隔（秒）
         */
        private int level1 = 90;
        
        /**
         * 级别2间隔（秒）
         */
        private int level2 = 300;
        
        /**
         * 级别3间隔（秒）
         */
        private int level3 = 600;
    }
    
    /**
     * 阈值配置内部类
     */
    @Data
    public static class Threshold {
        /**
         * 空计数1
         */
        private int emptyCount1 = 3;
        
        /**
         * 空计数2
         */
        private int emptyCount2 = 6;
    }

    
    /**
     * IMAP配置内部类
     */
    @Data
    public static class Imap {
        /**
         * IMAP主机
         */
        private String host = "imap.163.com";
        
        /**
         * IMAP端口
         */
        private int port = 993;
        
        /**
         * 用户名
         */
        private String username = "user@example.com";
        
        /**
         * 密码
         */
        private String password = "your_imap_app_password";
        
        /**
         * 是否启用SSL
         */
        private boolean ssl = true;
    }
    
    /**
     * RAG配置内部类
     */
    @Data
    public static class Rag {
        /**
         * 检索来源：local（本地 ES）| bailian（百炼知识库）
         */
        private String provider = "local";

        /**
         * TopK数量
         */
        private int topK = 5;
        
        /**
         * 最小分数
         */
        private double minScore = 0.25;
    }

    @Data
    public static class HistorySync {
        private boolean enabled = true;

        private int lookbackDays = 365;

        private int batchSize = 50;

        private int maxContextMessages = 20;

        private int maxContextChars = 12000;
    }
}
