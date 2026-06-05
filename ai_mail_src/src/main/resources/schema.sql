-- sql文件，如果和数据库实际结构有差异，以数据库实际ddl为准


-- 邮件账户表
CREATE TABLE IF NOT EXISTS mail_account
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    imap_host     VARCHAR(255) NOT NULL,
    imap_port     INT          NOT NULL,
    username      VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    use_ssl       TINYINT(1)            DEFAULT 1,

    last_sync_uid BIGINT                DEFAULT 0,
    uid_validity  BIGINT,
    last_sync_at  DATETIME,

    is_deleted    TINYINT(1)            DEFAULT 0,

    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_account (email, imap_host, imap_port),
    INDEX idx_last_sync_at (last_sync_at),
    INDEX idx_is_deleted (is_deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 邮件消息主表
CREATE TABLE IF NOT EXISTS mail_message
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    mail_account_id  BIGINT       NOT NULL,

    message_id       VARCHAR(512) NOT NULL,
    content_hash     VARCHAR(64),

    subject          VARCHAR(500),
    from_email       VARCHAR(255) NOT NULL,
    from_name        VARCHAR(255),

    to_emails        JSON,
    cc_emails        JSON,
    bcc_emails       JSON,

    in_reply_to      VARCHAR(512),
    mail_references  TEXT,
    thread_id        VARCHAR(255),

    body_html        LONGTEXT,
    body_text        LONGTEXT,

    has_attachment   TINYINT(1)            DEFAULT 0,
    attachment_count INT                   DEFAULT 0,

    sent_at          DATETIME,
    received_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    raw_headers      LONGTEXT,

    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    is_deleted       TINYINT(1)            DEFAULT 0,

    CONSTRAINT fk_mail_message_account
        FOREIGN KEY (mail_account_id) REFERENCES mail_account (id) ON DELETE CASCADE,

    UNIQUE KEY uk_message (mail_account_id, message_id),
    UNIQUE KEY uk_content_hash (mail_account_id, content_hash),

    INDEX idx_received_at (received_at),
    INDEX idx_from_email (from_email),
    INDEX idx_thread_id (thread_id),
    INDEX idx_is_deleted (is_deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 邮件附件表
CREATE TABLE IF NOT EXISTS mail_attachment
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    mail_message_id BIGINT       NOT NULL,

    filename        VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100),
    content_length  BIGINT,
    content_hash    VARCHAR(64),

    storage_path    VARCHAR(1024),
    storage_type    VARCHAR(50),

    is_scanned      TINYINT(1)            DEFAULT 0,
    is_downloaded   TINYINT(1)            DEFAULT 0,

    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attachment_message
        FOREIGN KEY (mail_message_id) REFERENCES mail_message (id) ON DELETE CASCADE,

    UNIQUE KEY uk_attachment_hash (mail_message_id, content_hash),
    INDEX idx_content_type (content_type),
    INDEX idx_is_downloaded (is_downloaded)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============================================
-- 知识库域（Knowledge Base Domain）
-- 设计原则：
-- 1. 只有知识库文档才会进行分片和向量化
-- 2. 邮件不参与 RAG 构建，不做 chunk，不做向量化
-- 3. chunk + 向量化需要事务保证
-- ============================================

-- 知识库文件表（document）
CREATE TABLE kb_document
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,

    file_md5          CHAR(32)     NOT NULL UNIQUE COMMENT '文件指纹',
    file_name         VARCHAR(255) NOT NULL COMMENT '文件名',
    total_size        BIGINT UNSIGNED COMMENT '文件大小（字节）',

    bucket_name       VARCHAR(128) NOT NULL COMMENT 'MinIO bucket 名称',
    raw_object_key    VARCHAR(512) NOT NULL COMMENT '源文件对象路径',
    parsed_object_key VARCHAR(512)          DEFAULT NULL COMMENT '解析后文件对象路径',

    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '0=上传中 1=已解析 2=已向量化 9=失败',
    user_id           VARCHAR(64) COMMENT '所属用户',

    created_at        DATETIME              DEFAULT CURRENT_TIMESTAMP,
    parsed_at         DATETIME COMMENT '解析完成时间',
    vectorized_at     DATETIME COMMENT '向量化完成时间',

    INDEX idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='知识库文件表';

-- 分片表
CREATE TABLE kb_document_chunk
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id  BIGINT   NOT NULL COMMENT '所属文档ID',
    chunk_index  INT      NOT NULL COMMENT '分片序号（0-based）',
    chunk_md5    CHAR(32) NOT NULL COMMENT 'chunk 内容指纹',
    text_content LONGTEXT NOT NULL COMMENT '原始文本',
    token_count  INT COMMENT 'token 数估算',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_chunk (document_id, chunk_index),
    INDEX idx_doc (document_id),
    FULLTEXT INDEX idx_text_content (text_content) WITH PARSER ngram COMMENT 'BM25 全文检索索引',
    CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id)
        REFERENCES kb_document (id)
) ENGINE = InnoDB COMMENT ='文档文本分片表';

-- 向量索引映射表
CREATE TABLE kb_vector_index
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_id      BIGINT       NOT NULL COMMENT 'chunk 主键',
    embedding_id  VARCHAR(128) NOT NULL COMMENT '向量库中的ID',
    model_version VARCHAR(64)  NOT NULL COMMENT 'embedding模型',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chunk_model (chunk_id, model_version),
    INDEX idx_chunk (chunk_id),
    CONSTRAINT fk_vector_chunk FOREIGN KEY (chunk_id)
        REFERENCES kb_document_chunk (id)
) ENGINE = InnoDB COMMENT ='向量索引映射表';

-- 用户表
CREATE TABLE IF NOT EXISTS users
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user          VARCHAR(255) NOT NULL,
    passwd        VARCHAR(255),
    password_hash VARCHAR(255),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_deleted    TINYINT(1)            DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user (user),
    INDEX idx_role (role),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- token 失效表（用于可选服务端登出）
CREATE TABLE IF NOT EXISTS token_invalidation
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti        VARCHAR(64)  NOT NULL,
    expires_at DATETIME     NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_jti (jti),
    INDEX idx_expires_at (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
