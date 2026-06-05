package com.github.mail.repo.AiRule.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 规则版本历史DTO
 * @author Asteries
 * @date 2026/01/05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleVersionHistoryDTO {
    /**
     * 规则id
     */
    private int ruleOrder;
    /**
     * 版本号
     */
    private String version;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}