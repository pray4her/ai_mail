package com.github.mail.repo.AiRule.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * AI 回复策略表
 *
 * @TableName ai_reply_strategy
 */
@TableName(value = "ai_reply_strategy")
@Data
public class AiReplyStrategy {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 语气
     */
    @TableField(value = "tone")
    private String tone;

    /**
     * 长度
     */
    @TableField(value = "length")
    private String length;

    /**
     * 是否包含步骤（1=包含，0=不包含）
     */
    @TableField(value = "include_steps")
    private Integer includeSteps;

    /**
     * 补充说明
     */
    @TableField(value = "extra_instruction")
    private String extraInstruction;

    /**
     * 创建时间
     */
    @TableField(value = "created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(value = "updated_time")
    private LocalDateTime updatedTime;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        AiReplyStrategy other = (AiReplyStrategy) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
                && (this.getTone() == null ? other.getTone() == null : this.getTone().equals(other.getTone()))
                && (this.getLength() == null ? other.getLength() == null : this.getLength().equals(other.getLength()))
                && (this.getIncludeSteps() == null ? other.getIncludeSteps() == null : this.getIncludeSteps().equals(other.getIncludeSteps()))
                && (this.getExtraInstruction() == null ? other.getExtraInstruction() == null : this.getExtraInstruction().equals(other.getExtraInstruction()))
                && (this.getCreatedTime() == null ? other.getCreatedTime() == null : this.getCreatedTime().equals(other.getCreatedTime()))
                && (this.getUpdatedTime() == null ? other.getUpdatedTime() == null : this.getUpdatedTime().equals(other.getUpdatedTime()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getTone() == null) ? 0 : getTone().hashCode());
        result = prime * result + ((getLength() == null) ? 0 : getLength().hashCode());
        result = prime * result + ((getIncludeSteps() == null) ? 0 : getIncludeSteps().hashCode());
        result = prime * result + ((getExtraInstruction() == null) ? 0 : getExtraInstruction().hashCode());
        result = prime * result + ((getCreatedTime() == null) ? 0 : getCreatedTime().hashCode());
        result = prime * result + ((getUpdatedTime() == null) ? 0 : getUpdatedTime().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", tone=").append(tone);
        sb.append(", length=").append(length);
        sb.append(", includeSteps=").append(includeSteps);
        sb.append(", extraInstruction=").append(extraInstruction);
        sb.append(", createdTime=").append(createdTime);
        sb.append(", updatedTime=").append(updatedTime);
        sb.append("]");
        return sb.toString();
    }
}