package com.github.mail.model.config;

import lombok.Data;

/**
 * 阿里云百炼知识库配置（动态配置，从 config.json 读取）
 *
 * @author System
 */
@Data
public class BailianConfig {

    private String accessKeyId = "";

    private String accessKeySecret = "";

    /** 业务空间 ID，如 llm-xxxx */
    private String workspaceId = "";

    /** 知识库 ID，控制台或 ListIndices API 获取 */
    private String indexId = "";

    private String endpoint = "bailian.cn-beijing.aliyuncs.com";
}
