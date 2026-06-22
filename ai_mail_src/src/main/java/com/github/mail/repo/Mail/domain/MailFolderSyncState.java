package com.github.mail.repo.Mail.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "mail_folder_sync_state")
public class MailFolderSyncState {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "mail_account_id")
    private Long mailAccountId;

    @TableField(value = "folder_name")
    private String folderName;

    @TableField(value = "sync_scope")
    private String syncScope;

    @TableField(value = "uid_validity")
    private Long uidValidity;

    @TableField(value = "last_synced_uid")
    private Long lastSyncedUid;

    @TableField(value = "sync_status")
    private String syncStatus;

    @TableField(value = "last_error")
    private String lastError;

    @TableField(value = "started_at")
    private LocalDateTime startedAt;

    @TableField(value = "completed_at")
    private LocalDateTime completedAt;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
