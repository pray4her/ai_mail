DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing$$
CREATE PROCEDURE add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN ', column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_index_if_missing$$
CREATE PROCEDURE add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN index_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_column_if_missing('mail_account', 'history_synced', '`history_synced` TINYINT(1) NOT NULL DEFAULT 0');
CALL add_column_if_missing('mail_account', 'history_sync_started_at', '`history_sync_started_at` DATETIME NULL');
CALL add_column_if_missing('mail_account', 'history_sync_completed_at', '`history_sync_completed_at` DATETIME NULL');
CALL add_column_if_missing('mail_account', 'history_sync_error', '`history_sync_error` TEXT NULL');

CREATE TABLE IF NOT EXISTS mail_folder_sync_state (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mail_account_id BIGINT NOT NULL,
  folder_name VARCHAR(255) NOT NULL,
  sync_scope VARCHAR(50) NOT NULL,
  uid_validity BIGINT NULL,
  last_synced_uid BIGINT NULL DEFAULT 0,
  sync_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  last_error TEXT NULL,
  started_at DATETIME NULL,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_folder_scope (mail_account_id, folder_name, sync_scope),
  INDEX idx_sync_status (sync_status),
  CONSTRAINT fk_folder_sync_account FOREIGN KEY (mail_account_id) REFERENCES mail_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL add_column_if_missing('mail_message', 'folder_name', '`folder_name` VARCHAR(255) NULL');
CALL add_column_if_missing('mail_message', 'imap_uid', '`imap_uid` BIGINT NULL');
CALL add_column_if_missing('mail_message', 'folder_uid_validity', '`folder_uid_validity` BIGINT NULL');
CALL add_column_if_missing('mail_message', 'direction', '`direction` VARCHAR(20) NOT NULL DEFAULT ''INBOUND''');
CALL add_column_if_missing('mail_message', 'is_history', '`is_history` TINYINT(1) NOT NULL DEFAULT 0');
CALL add_column_if_missing('mail_message', 'raw_mime_storage_path', '`raw_mime_storage_path` VARCHAR(1024) NULL');
CALL add_index_if_missing('mail_message', 'uk_folder_uid', 'UNIQUE KEY `uk_folder_uid` (`mail_account_id`, `folder_name`, `folder_uid_validity`, `imap_uid`)');

CALL add_column_if_missing('mail_attachment', 'attachment_kind', '`attachment_kind` VARCHAR(50) NOT NULL DEFAULT ''MINIO''');
CALL add_column_if_missing('mail_attachment', 'external_url', '`external_url` VARCHAR(2048) NULL');
CALL add_column_if_missing('mail_attachment', 'expires_at', '`expires_at` DATETIME NULL');
CALL add_column_if_missing('mail_attachment', 'remark', '`remark` VARCHAR(500) NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
