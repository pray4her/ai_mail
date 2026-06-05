package com.github.mail.service.Config;

import com.github.mail.model.config.AppConfig;
import com.github.mail.model.config.MailConfig;
import com.github.mail.model.config.DeepSeekConfig;
import com.github.mail.model.config.EmbeddingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 配置服务类
 * 负责配置的业务逻辑和参数校验
 * 
 * @author System
 * @date 2026/01/06
 */
@Slf4j
@Service
public class ConfigService {
    
    /**
     * 获取配置
     * 
     * @return 配置对象
     */
    public AppConfig getConfig() {
        return ConfigFileManager.readConfig();
    }
    
    /**
     * 保存配置
     * 
     * @param config 配置对象
     */
    public void saveConfig(AppConfig config) {
        // 参数校验
        validateConfig(config);
        
        // 保存配置
        ConfigFileManager.writeConfig(config);
        log.info("配置保存成功");
    }
    
    /**
     * 校验配置参数
     * 
     * @param config 配置对象
     * @throws IllegalArgumentException 校验失败时抛出
     */
    private void validateConfig(AppConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("配置对象不能为空");
        }
        
        // 校验邮件配置
        validateMailConfig(config.getMail());

        // 校验DeepSeek配置
        validateDeepSeekConfig(config.getDeepseek());
        
        // 校验Embedding配置
        validateEmbeddingConfig(config.getEmbedding());

        
        log.info("配置校验通过");
    }
    

    
    /**
     * 校验邮件配置
     */
    private void validateMailConfig(MailConfig mailConfig) {
        if (mailConfig == null) {
            throw new IllegalArgumentException("邮件配置不能为空");
        }
        
        // 校验IMAP配置
        MailConfig.Imap imap = mailConfig.getImapList().get(0);
        if (imap == null) {
            throw new IllegalArgumentException("IMAP配置不能为空");
        }
        
        if (isEmpty(imap.getHost())) {
            throw new IllegalArgumentException("IMAP主机不能为空");
        }
        
        if (imap.getPort() <= 0 || imap.getPort() > 65535) {
            throw new IllegalArgumentException("IMAP端口应在1-65535范围内");
        }
        
        if (isEmpty(imap.getUsername())) {
            throw new IllegalArgumentException("IMAP用户名不能为空");
        }
        
        if (isEmpty(imap.getPassword())) {
            throw new IllegalArgumentException("IMAP密码不能为空");
        }
        
        // 校验自动回复配置
        MailConfig.AutoReply autoReply = mailConfig.getAutoReply();
        if (autoReply != null) {
            MailConfig.Interval interval = autoReply.getInterval();
            if (interval != null) {
                if (interval.getLevel1() <= 0) {
                    throw new IllegalArgumentException("自动回复级别1间隔应大于0");
                }
                if (interval.getLevel2() <= 0) {
                    throw new IllegalArgumentException("自动回复级别2间隔应大于0");
                }
                if (interval.getLevel3() <= 0) {
                    throw new IllegalArgumentException("自动回复级别3间隔应大于0");
                }
            }
            
            MailConfig.Threshold threshold = autoReply.getThreshold();
            if (threshold != null) {
                if (threshold.getEmptyCount1() < 0) {
                    throw new IllegalArgumentException("空计数1不能为负数");
                }
                if (threshold.getEmptyCount2() < 0) {
                    throw new IllegalArgumentException("空计数2不能为负数");
                }
            }
            
            // RAG配置在MailConfig层级，不在AutoReply内部
            MailConfig.Rag rag = mailConfig.getRag();
            if (rag != null) {
                if (rag.getTopK() <= 0) {
                    throw new IllegalArgumentException("TopK应大于0");
                }
                if (rag.getMinScore() < 0 || rag.getMinScore() > 1) {
                    throw new IllegalArgumentException("最小分数应在0-1范围内");
                }
            }
        }
    }
    
    /**
     * 校验DeepSeek配置
     */
    private void validateDeepSeekConfig(DeepSeekConfig deepseekConfig) {
        if (deepseekConfig == null) {
            throw new IllegalArgumentException("DeepSeek配置不能为空");
        }
        
        if (isEmpty(deepseekConfig.getApiUrl())) {
            throw new IllegalArgumentException("DeepSeek API URL不能为空");
        }
        
        if (isEmpty(deepseekConfig.getModel())) {
            throw new IllegalArgumentException("DeepSeek模型名称不能为空");
        }
        
        // 简单的URL格式校验
        if (!deepseekConfig.getApiUrl().startsWith("http://") && !deepseekConfig.getApiUrl().startsWith("https://")) {
            throw new IllegalArgumentException("DeepSeek API URL格式不正确，应以http://或https://开头");
        }
    }
    
    /**
     * 校验Embedding配置
     */
    private void validateEmbeddingConfig(EmbeddingConfig embeddingConfig) {
        if (embeddingConfig == null) {
            throw new IllegalArgumentException("Embedding配置不能为空");
        }
        
        EmbeddingConfig.Ali ali = embeddingConfig.getAli();
        if (ali == null) {
            throw new IllegalArgumentException("阿里云Embedding配置不能为空");
        }
        
        if (isEmpty(ali.getApiUrl())) {
            throw new IllegalArgumentException("Embedding API URL不能为空");
        }
        
        if (isEmpty(ali.getModel())) {
            throw new IllegalArgumentException("Embedding模型名称不能为空");
        }
        
        if (ali.getBatchSize() <= 0 || ali.getBatchSize() > 1000) {
            throw new IllegalArgumentException("批处理大小应在1-1000范围内");
        }
        
        if (ali.getDimension() <= 0) {
            throw new IllegalArgumentException("向量维度应大于0");
        }
        
        // 简单的URL格式校验
        if (!ali.getApiUrl().startsWith("http://") && !ali.getApiUrl().startsWith("https://")) {
            throw new IllegalArgumentException("Embedding API URL格式不正确，应以http://或https://开头");
        }
    }

    
    /**
     * 检查字符串是否为空或空白
     */
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
