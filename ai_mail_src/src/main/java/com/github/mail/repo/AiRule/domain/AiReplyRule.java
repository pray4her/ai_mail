package com.github.mail.repo.AiRule.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 回复规则表
 * @TableName ai_reply_rule
 */
@TableName(value ="ai_reply_rule")
@Data
public class AiReplyRule {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 规则顺序
     */
    @TableField(value = "rule_order")
    private Integer ruleOrder;

    /**
     * 规则文本
     */
    @TableField(value = "rule_text")
    private String ruleText;

    /**
     * 是否启用（1=启用，0=禁用）
     */
    @TableField(value = "enabled")
    private Integer enabled;

    /**
     * 版本号
     */
    @TableField(value = "version")
    private String version;

    /**
     * 更新人
     */
    @TableField(value = "updated_by")
    private String updatedBy;

    /**
     * 是否核心规则（不可删除）
     */
    @TableField(value = "is_core")
    private Integer isCore;

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
        AiReplyRule other = (AiReplyRule) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getRuleOrder() == null ? other.getRuleOrder() == null : this.getRuleOrder().equals(other.getRuleOrder()))
            && (this.getRuleText() == null ? other.getRuleText() == null : this.getRuleText().equals(other.getRuleText()))
            && (this.getEnabled() == null ? other.getEnabled() == null : this.getEnabled().equals(other.getEnabled()))
            && (this.getVersion() == null ? other.getVersion() == null : this.getVersion().equals(other.getVersion()))
            && (this.getUpdatedBy() == null ? other.getUpdatedBy() == null : this.getUpdatedBy().equals(other.getUpdatedBy()))
            && (this.getIsCore() == null ? other.getIsCore() == null : this.getIsCore().equals(other.getIsCore()))
            && (this.getCreatedTime() == null ? other.getCreatedTime() == null : this.getCreatedTime().equals(other.getCreatedTime()))
            && (this.getUpdatedTime() == null ? other.getUpdatedTime() == null : this.getUpdatedTime().equals(other.getUpdatedTime()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getRuleOrder() == null) ? 0 : getRuleOrder().hashCode());
        result = prime * result + ((getRuleText() == null) ? 0 : getRuleText().hashCode());
        result = prime * result + ((getEnabled() == null) ? 0 : getEnabled().hashCode());
        result = prime * result + ((getVersion() == null) ? 0 : getVersion().hashCode());
        result = prime * result + ((getUpdatedBy() == null) ? 0 : getUpdatedBy().hashCode());
        result = prime * result + ((getIsCore() == null) ? 0 : getIsCore().hashCode());
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
        sb.append(", ruleOrder=").append(ruleOrder);
        sb.append(", ruleText=").append(ruleText);
        sb.append(", enabled=").append(enabled);
        sb.append(", version=").append(version);
        sb.append(", updatedBy=").append(updatedBy);
        sb.append(", isCore=").append(isCore);
        sb.append(", createdTime=").append(createdTime);
        sb.append(", updatedTime=").append(updatedTime);
        sb.append("]");
        return sb.toString();
    }
}