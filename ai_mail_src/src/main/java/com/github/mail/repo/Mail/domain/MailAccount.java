package com.github.mail.repo.Mail.domain;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 邮箱账户表
 * @author Asteries
 * @TableName mail_account
 */
@TableName(value = "mail_account")
@Data
public class MailAccount {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户邮箱
     */
    @TableField(value = "email")
    private String email;

    /**
     * imap服务器地址
     */
    @TableField(value = "imap_host")
    private String imapHost;

    /**
     * imap服务器端口
     */
    @TableField(value = "imap_port")
    private Integer imapPort;

    /**
     * 用户名（目前使用用户邮箱）
     */
    @TableField(value = "username")
    private String username;

    /**
     * 用户邮箱授权码
     */
    @TableField(value = "password")
    private String password;

    /**
     * 是否使用SSl
     */
    @TableField(value = "use_ssl")
    private Integer useSsl;

    /**
     * 最后同步（拉取）时间
     */
    @TableField(value = "last_sync_at")
    private LocalDateTime lastSyncAt;

    /**
     * 最后同步的UID
     */
    @TableField(value = "last_sync_uid")
    private Long lastSyncUid;


    /**
     * uid有效性
     */
    @TableField(value = "uid_validity")
    private Long uidValidity;

    /**
     * 软删除标记
     */
    @TableField(value = "is_deleted")
    private Integer isDeleted;

    /**
     * 记录创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 记录更新时间
     */
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;


    @Override
    public String toString() {
        return "MailAccount{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", imapHost='" + imapHost + '\'' +
                ", imapPort=" + imapPort +
                ", username='" + username + '\'' +
                ", lastSyncUid=" + lastSyncUid +
                ", lastSyncAt=" + lastSyncAt +
                ", createdAt=" + createdAt +
                '}';
    }

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
        MailAccount other = (MailAccount) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
                && (this.getEmail() == null ? other.getEmail() == null : this.getEmail().equals(other.getEmail()))
                && (this.getImapHost() == null ? other.getImapHost() == null : this.getImapHost().equals(other.getImapHost()))
                && (this.getImapPort() == null ? other.getImapPort() == null : this.getImapPort().equals(other.getImapPort()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getEmail() == null) ? 0 : getEmail().hashCode());
        result = prime * result + ((getImapHost() == null) ? 0 : getImapHost().hashCode());
        result = prime * result + ((getImapPort() == null) ? 0 : getImapPort().hashCode());
        return result;
    }
}