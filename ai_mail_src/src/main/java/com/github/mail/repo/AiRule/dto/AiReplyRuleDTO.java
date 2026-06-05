package com.github.mail.repo.AiRule.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI回复规则DTO
 * @author Asteries
 * @date 2026/01/05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReplyRuleDTO {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 规则顺序
     */
    private Integer ruleOrder;

    /**
     * 规则文本
     */
    private String ruleText;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 版本号
     */
    private String version;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 是否核心规则（不可删除）
     */
    private Boolean isCore;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}