package com.github.mail.repo.AiRule.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI回复策略DTO
 * @author Asteries
 * @date 2026/01/05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReplyStrategyDTO {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 语气
     */
    private String tone;

    /**
     * 长度
     */
    private String length;

    /**
     * 是否包含步骤
     */
    private Boolean includeSteps;

    /**
     * 补充说明
     */
    private String extraInstruction;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}