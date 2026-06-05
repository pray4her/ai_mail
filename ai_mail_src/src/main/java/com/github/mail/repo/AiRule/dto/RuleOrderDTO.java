package com.github.mail.repo.AiRule.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则顺序DTO
 * @author Asteries
 * @date 2026/01/05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleOrderDTO {
    /**
     * 规则ID
     */
    private Long id;

    /**
     * 规则顺序
     */
    private Integer ruleOrder;
}