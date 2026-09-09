-- =============================================================
-- File Manager App - MySQL Schema Initialization
-- =============================================================
-- This file is executed once when the MySQL container first starts.
-- It creates all required tables and seeds an initial admin user.
--
-- Table names and column names must match what the repository
-- classes (MySQLUserRepository, MySQLFileRepository, etc.) query.
-- =============================================================

CREATE DATABASE IF NOT EXISTS filemanager;
USE filemanager;

-- =============================================================
-- USERS
-- Referenced by: MySQLUserRepository
-- Columns: id, username, password_hash, role, created_at
-- =============================================================
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('STANDARD', 'ADMIN') NOT NULL DEFAULT 'STANDARD',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- FILES
-- Referenced by: MySQLFileRepository
-- Columns: id, owner_id, filename, size_bytes, created_at, last_modified
-- =============================================================
CREATE TABLE IF NOT EXISTS files (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    owner_id      BIGINT        NOT NULL,
    filename      VARCHAR(255)  NOT NULL,
    size_bytes    BIGINT        NOT NULL DEFAULT 0,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_files_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- FILE CHUNKS
-- Referenced by: MySQLFileRepository (saveChunk, getChunksForFile, deleteChunksByFileId)
-- Columns: id, file_id, chunk_order, crc32_checksum, storage_location, volume_group
-- =============================================================
CREATE TABLE IF NOT EXISTS file_chunks (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    file_id          BIGINT      NOT NULL,
    chunk_order      INT         NOT NULL,   -- 1, 2, 3, or 4
    crc32_checksum   VARCHAR(20) NOT NULL,
    storage_location VARCHAR(100) NOT NULL,  -- FS container ID e.g. "fs-1"
    volume_group     INT         NOT NULL,   -- 1, 2, 3, or 4
    PRIMARY KEY (id),
    CONSTRAINT fk_chunks_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    UNIQUE KEY uq_chunk (file_id, chunk_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- FILE PERMISSIONS
-- Referenced by: MySQLPermissionRepository
-- Columns: id, file_id, user_id, permission_type, granted_at
-- =============================================================
CREATE TABLE IF NOT EXISTS file_permissions (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    file_id         BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    permission_type ENUM('READ', 'READ_WRITE') NOT NULL DEFAULT 'READ',
    granted_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_perms_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT fk_perms_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    UNIQUE KEY uq_file_user_perm (file_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- EVENT LOGS
-- Referenced by: MysqlEventLogger, MySQLEventLogRepository
-- Columns: log_id, user_id, event_type, description, timestamp
-- =============================================================
CREATE TABLE IF NOT EXISTS event_logs (
    log_id      BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NULL,           -- NULL for system events
    event_type  VARCHAR(100)  NOT NULL,
    description TEXT          NOT NULL,
    timestamp   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    CONSTRAINT fk_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================
-- SEED DATA
-- Insert a default admin user.
-- Password: "admin123" hashed with BCrypt (cost=10).
-- The app uses org.mindrot.jbcrypt to verify this hash.
-- To change the password, regenerate the BCrypt hash and update here.
-- =============================================================
INSERT INTO users (username, password_hash, role, created_at)
VALUES (
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  -- "admin123"
    'ADMIN',
    NOW()
)
ON DUPLICATE KEY UPDATE username = username;  -- no-op if already exists
