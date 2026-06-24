CREATE TABLE mail_account (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  imap_host VARCHAR(255) NOT NULL,
  imap_port INT NOT NULL,
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL DEFAULT '0',
  use_ssl TINYINT(1) DEFAULT 1,
  last_sync_uid BIGINT DEFAULT 0,
  uid_validity BIGINT DEFAULT NULL,
  last_sync_at DATETIME DEFAULT NULL,
  history_synced TINYINT(1) NOT NULL DEFAULT 0,
  history_sync_started_at DATETIME DEFAULT NULL,
  history_sync_completed_at DATETIME DEFAULT NULL,
  history_sync_error TEXT DEFAULT NULL,
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_account (email, imap_host, imap_port),
  KEY idx_last_sync_at (last_sync_at),
  KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mail_folder_sync_state (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mail_account_id BIGINT NOT NULL,
  folder_name VARCHAR(255) NOT NULL,
  sync_scope VARCHAR(50) NOT NULL,
  uid_validity BIGINT DEFAULT NULL,
  last_synced_uid BIGINT DEFAULT 0,
  sync_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  last_error TEXT DEFAULT NULL,
  started_at DATETIME DEFAULT NULL,
  completed_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_folder_scope (mail_account_id, folder_name, sync_scope),
  KEY idx_sync_status (sync_status),
  CONSTRAINT fk_folder_sync_account FOREIGN KEY (mail_account_id) REFERENCES mail_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mail_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mail_account_id BIGINT NOT NULL,
  message_id VARCHAR(512) NOT NULL,
  folder_name VARCHAR(255) DEFAULT NULL,
  imap_uid BIGINT DEFAULT NULL,
  folder_uid_validity BIGINT DEFAULT NULL,
  content_hash VARCHAR(64) DEFAULT NULL,
  subject VARCHAR(500) DEFAULT NULL,
  from_email VARCHAR(255) NOT NULL,
  from_name VARCHAR(255) DEFAULT NULL,
  to_emails JSON DEFAULT NULL,
  cc_emails JSON DEFAULT NULL,
  bcc_emails JSON DEFAULT NULL,
  in_reply_to VARCHAR(512) DEFAULT NULL,
  mail_references TEXT DEFAULT NULL,
  thread_id VARCHAR(255) DEFAULT NULL,
  body_html LONGTEXT DEFAULT NULL,
  body_text LONGTEXT DEFAULT NULL,
  direction VARCHAR(20) NOT NULL DEFAULT 'INBOUND',
  is_history TINYINT(1) NOT NULL DEFAULT 0,
  raw_mime_storage_path VARCHAR(1024) DEFAULT NULL,
  has_attachment TINYINT(1) NOT NULL DEFAULT 0,
  attachment_count INT NOT NULL DEFAULT 0,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  is_flagged TINYINT(1) NOT NULL DEFAULT 0,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  sent_at DATETIME DEFAULT NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  raw_headers LONGTEXT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_message (mail_account_id, message_id),
  UNIQUE KEY uk_folder_uid (mail_account_id, folder_name, folder_uid_validity, imap_uid),
  UNIQUE KEY uk_content_hash (mail_account_id, content_hash),
  KEY idx_received_at (received_at),
  KEY idx_from_email (from_email),
  KEY idx_thread_id (thread_id),
  KEY idx_is_deleted (is_deleted),
  CONSTRAINT fk_mail_message_account FOREIGN KEY (mail_account_id) REFERENCES mail_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mail_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mail_message_id BIGINT NOT NULL,
  filename VARCHAR(500) NOT NULL,
  content_type VARCHAR(100) DEFAULT NULL,
  content_length BIGINT DEFAULT NULL,
  content_hash VARCHAR(64) DEFAULT NULL,
  storage_path VARCHAR(1024) DEFAULT NULL,
  storage_type VARCHAR(50) DEFAULT NULL,
  attachment_kind VARCHAR(50) NOT NULL DEFAULT 'MINIO',
  external_url VARCHAR(2048) DEFAULT NULL,
  expires_at DATETIME DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  is_scanned TINYINT(1) DEFAULT 0,
  is_downloaded TINYINT(1) DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_attachment_hash (mail_message_id, content_hash),
  KEY idx_content_type (content_type),
  KEY idx_is_downloaded (is_downloaded),
  CONSTRAINT fk_attachment_message FOREIGN KEY (mail_message_id) REFERENCES mail_message (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mail_processing_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mail_message_id BIGINT DEFAULT NULL,
  mail_account_id BIGINT DEFAULT NULL,
  message_id_snapshot VARCHAR(255) DEFAULT NULL,
  subject VARCHAR(500) DEFAULT NULL,
  from_email VARCHAR(255) DEFAULT NULL,
  thread_id VARCHAR(255) DEFAULT NULL,
  processing_type VARCHAR(100) DEFAULT NULL,
  processing_version INT DEFAULT NULL,
  reply_status VARCHAR(50) DEFAULT NULL,
  reply_content TEXT DEFAULT NULL,
  reply_draft_folder VARCHAR(255) DEFAULT NULL,
  handled_by VARCHAR(100) DEFAULT NULL,
  processed_at DATETIME DEFAULT NULL,
  error_message TEXT DEFAULT NULL,
  remarks TEXT DEFAULT NULL,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_mail_message_id (mail_message_id),
  KEY idx_mail_account_id (mail_account_id),
  KEY idx_reply_status (reply_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮件处理记录表';

CREATE TABLE kb_document (
  id BIGINT NOT NULL AUTO_INCREMENT,
  file_md5 CHAR(32) NOT NULL COMMENT '文件指纹',
  file_name VARCHAR(255) NOT NULL COMMENT '文件名',
  total_size BIGINT UNSIGNED DEFAULT NULL COMMENT '文件大小（字节）',
  bucket_name VARCHAR(128) NOT NULL COMMENT 'MinIO bucket 名称',
  raw_object_key VARCHAR(512) NOT NULL COMMENT '源文件对象路径',
  parsed_object_key VARCHAR(512) DEFAULT NULL COMMENT '解析后文件对象路径',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=上传中 1=已解析 2=已向量化 9=失败',
  user_id VARCHAR(64) DEFAULT NULL COMMENT '所属用户',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  parsed_at DATETIME DEFAULT NULL COMMENT '解析完成时间',
  vectorized_at DATETIME DEFAULT NULL COMMENT '向量化完成时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_md5 (file_md5),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文件表';

CREATE TABLE kb_document_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id BIGINT NOT NULL COMMENT '所属文档ID',
  chunk_index INT NOT NULL COMMENT '分片序号（0-based）',
  chunk_md5 CHAR(32) NOT NULL COMMENT 'chunk 内容指纹',
  text_content LONGTEXT NOT NULL COMMENT '原始文本',
  token_count INT DEFAULT NULL COMMENT 'token 数估算',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_chunk (document_id, chunk_index),
  KEY idx_doc (document_id),
  FULLTEXT KEY idx_text_content (text_content) WITH PARSER ngram,
  CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES kb_document (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档文本分片表';

CREATE TABLE kb_vector_index (
  id BIGINT NOT NULL AUTO_INCREMENT,
  chunk_id BIGINT NOT NULL COMMENT 'chunk 主键',
  embedding_id VARCHAR(128) NOT NULL DEFAULT '0' COMMENT '向量库中的ID',
  embedding_vector LONGTEXT DEFAULT NULL COMMENT '向量数据(JSON 格式，仅测试使用)',
  model_version VARCHAR(64) NOT NULL COMMENT 'embedding模型',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chunk_model (chunk_id, model_version),
  KEY idx_chunk (chunk_id),
  CONSTRAINT fk_vector_chunk FOREIGN KEY (chunk_id) REFERENCES kb_document_chunk (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='向量索引映射表';

CREATE TABLE kb_tag (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL COMMENT '标签名称',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

CREATE TABLE kb_document_tag (
  document_id BIGINT NOT NULL COMMENT '文档ID',
  tag_id BIGINT NOT NULL COMMENT '标签ID',
  PRIMARY KEY (document_id, tag_id),
  KEY fk_tag (tag_id),
  CONSTRAINT fk_document FOREIGN KEY (document_id) REFERENCES kb_document (id) ON DELETE CASCADE,
  CONSTRAINT fk_tag FOREIGN KEY (tag_id) REFERENCES kb_tag (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档-标签关联表';

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user VARCHAR(255) NOT NULL,
  passwd VARCHAR(255) DEFAULT NULL,
  password_hash VARCHAR(255) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user),
  KEY idx_role (role),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE token_invalidation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  jti VARCHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_jti (jti),
  KEY idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
