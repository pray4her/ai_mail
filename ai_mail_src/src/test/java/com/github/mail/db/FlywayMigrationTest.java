package com.github.mail.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("mail")
            .withUsername("mail")
            .withPassword("mail");

    @Test
    void should_migrate_empty_database_to_current_schema() throws Exception {
        String jdbcUrl = cleanDatabase();

        Flyway flyway = flyway(jdbcUrl, false);
        flyway.migrate();
        int migrationRowsAfterFirstRun = queryForInt(jdbcUrl, "SELECT COUNT(*) FROM flyway_schema_history");

        flyway.migrate();

        assertThat(tableExists(jdbcUrl, "mail_account")).isTrue();
        assertThat(tableExists(jdbcUrl, "kb_document_tag")).isTrue();
        assertThat(columnExists(jdbcUrl, "mail_message", "is_read")).isTrue();
        assertThat(columnExists(jdbcUrl, "kb_vector_index", "embedding_vector")).isTrue();
        assertThat(indexExists(jdbcUrl, "mail_message", "uk_folder_uid")).isTrue();
        assertThat(foreignKeyExists(jdbcUrl, "mail_attachment", "fk_attachment_message")).isTrue();
        assertThat(queryForInt(jdbcUrl, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")).isEqualTo(2);
        assertThat(queryForInt(jdbcUrl, "SELECT COUNT(*) FROM flyway_schema_history")).isEqualTo(migrationRowsAfterFirstRun);
    }

    @Test
    void should_baseline_existing_database_and_apply_adoption_migration_without_data_loss() throws Exception {
        String jdbcUrl = cleanDatabase();
        createLegacySchema(jdbcUrl);

        Flyway.configure()
                .dataSource(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();

        assertThat(queryForInt(jdbcUrl, "SELECT COUNT(*) FROM mail_account")).isEqualTo(1);
        assertThat(columnExists(jdbcUrl, "mail_account", "history_synced")).isTrue();
        assertThat(columnExists(jdbcUrl, "mail_message", "folder_name")).isTrue();
        assertThat(columnExists(jdbcUrl, "mail_attachment", "external_url")).isTrue();
        assertThat(columnExists(jdbcUrl, "kb_vector_index", "embedding_vector")).isTrue();
        assertThat(tableExists(jdbcUrl, "mail_folder_sync_state")).isTrue();
        assertThat(tableExists(jdbcUrl, "mail_processing_record")).isTrue();
        assertThat(tableExists(jdbcUrl, "kb_document_tag")).isTrue();
        assertThat(indexExists(jdbcUrl, "mail_message", "uk_folder_uid")).isTrue();
        assertThat(queryForInt(jdbcUrl, "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = 1"))
                .isEqualTo(1);
    }

    @Test
    void should_fail_validation_when_applied_migration_checksum_changes(@TempDir Path tempDir) throws Exception {
        String jdbcUrl = cleanDatabase();
        Path migrationDir = tempDir.resolve("db/migration");
        Files.createDirectories(migrationDir);
        Path migration = migrationDir.resolve("V1__checksum_guard.sql");
        Files.writeString(migration, "CREATE TABLE checksum_guard (id INT NOT NULL PRIMARY KEY);\n");

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDir)
                .load();
        flyway.migrate();

        Files.writeString(migration, "CREATE TABLE checksum_guard (id BIGINT NOT NULL PRIMARY KEY);\n");

        assertThatThrownBy(flyway::validate).isInstanceOf(FlywayValidateException.class);
    }

    private static Flyway flyway(String jdbcUrl, boolean baselineOnMigrate) {
        return Flyway.configure()
                .dataSource(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(baselineOnMigrate)
                .validateOnMigrate(true)
                .load();
    }

    private static String cleanDatabase() {
        String jdbcUrl = MYSQL.getJdbcUrl();
        Flyway.configure()
                .dataSource(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
        return jdbcUrl;
    }

    private static boolean tableExists(String jdbcUrl, String tableName) throws SQLException {
        return queryForInt(jdbcUrl, """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                """.formatted(tableName)) == 1;
    }

    private static boolean columnExists(String jdbcUrl, String tableName, String columnName) throws SQLException {
        return queryForInt(jdbcUrl, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                  AND column_name = '%s'
                """.formatted(tableName, columnName)) == 1;
    }

    private static boolean indexExists(String jdbcUrl, String tableName, String indexName) throws SQLException {
        return queryForInt(jdbcUrl, """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                  AND index_name = '%s'
                """.formatted(tableName, indexName)) > 0;
    }

    private static boolean foreignKeyExists(String jdbcUrl, String tableName, String constraintName) throws SQLException {
        return queryForInt(jdbcUrl, """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                  AND constraint_name = '%s'
                  AND constraint_type = 'FOREIGN KEY'
                """.formatted(tableName, constraintName)) == 1;
    }

    private static int queryForInt(String jdbcUrl, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static void createLegacySchema(String jdbcUrl) throws SQLException {
        List<String> statements = List.of(
                """
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
                  is_deleted TINYINT(1) DEFAULT 0,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_account (email, imap_host, imap_port)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE mail_message (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  mail_account_id BIGINT NOT NULL,
                  message_id VARCHAR(512) NOT NULL,
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
                  has_attachment TINYINT(1) NOT NULL DEFAULT 0,
                  attachment_count INT NOT NULL DEFAULT 0,
                  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                  sent_at DATETIME DEFAULT NULL,
                  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  raw_headers LONGTEXT DEFAULT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_message (mail_account_id, message_id),
                  UNIQUE KEY uk_content_hash (mail_account_id, content_hash),
                  CONSTRAINT fk_mail_message_account FOREIGN KEY (mail_account_id) REFERENCES mail_account (id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE mail_attachment (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  mail_message_id BIGINT NOT NULL,
                  filename VARCHAR(500) NOT NULL,
                  content_type VARCHAR(100) DEFAULT NULL,
                  content_length BIGINT DEFAULT NULL,
                  content_hash VARCHAR(64) DEFAULT NULL,
                  storage_path VARCHAR(1024) DEFAULT NULL,
                  storage_type VARCHAR(50) DEFAULT NULL,
                  is_scanned TINYINT(1) DEFAULT 0,
                  is_downloaded TINYINT(1) DEFAULT 0,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_attachment_hash (mail_message_id, content_hash),
                  CONSTRAINT fk_attachment_message FOREIGN KEY (mail_message_id) REFERENCES mail_message (id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE kb_document (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  file_md5 CHAR(32) NOT NULL,
                  file_name VARCHAR(255) NOT NULL,
                  total_size BIGINT UNSIGNED DEFAULT NULL,
                  bucket_name VARCHAR(128) DEFAULT NULL,
                  raw_object_key VARCHAR(512) DEFAULT NULL,
                  parsed_object_key VARCHAR(512) DEFAULT NULL,
                  status TINYINT NOT NULL DEFAULT 0,
                  user_id VARCHAR(64) DEFAULT NULL,
                  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                  parsed_at DATETIME DEFAULT NULL,
                  vectorized_at DATETIME DEFAULT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_file_md5 (file_md5)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE kb_document_chunk (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  document_id BIGINT NOT NULL,
                  chunk_index INT NOT NULL,
                  chunk_md5 CHAR(32) NOT NULL,
                  text_content LONGTEXT,
                  token_count INT DEFAULT NULL,
                  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_doc_chunk (document_id, chunk_index),
                  CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES kb_document (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE kb_vector_index (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  chunk_id BIGINT NOT NULL,
                  embedding_id VARCHAR(128) NOT NULL DEFAULT '0',
                  model_version VARCHAR(64) NOT NULL,
                  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_chunk_model (chunk_id, model_version),
                  CONSTRAINT fk_vector_chunk FOREIGN KEY (chunk_id) REFERENCES kb_document_chunk (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE users (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user VARCHAR(255) NOT NULL,
                  passwd VARCHAR(255) DEFAULT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_user (user)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE token_invalidation (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  jti VARCHAR(64) NOT NULL,
                  expires_at DATETIME NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_jti (jti)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                "INSERT INTO mail_account (email, imap_host, imap_port, username, password) VALUES ('ops@example.com', 'imap.example.com', 993, 'ops@example.com', 'secret')");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }
}
