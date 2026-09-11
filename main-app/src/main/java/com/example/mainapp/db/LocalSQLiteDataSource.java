package com.example.mainapp.db;

import com.example.mainapp.config.MainAppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SQLite data source for offline/local mode.
 *
 * Creates and initialises the local database schema on first connection.
 * All tables are created with IF NOT EXISTS so repeated startups are safe.
 *
 * Schema mirrors the MySQL schema in config/mysql-init.sql with SQLite-compatible types.
 *
 * Tables used by SyncRepository:
 *   cached_files, cached_users, cached_permissions, pending_changes
 *
 * Tables used by SyncService (pending_changes) and other offline-aware services.
 */
public class LocalSQLiteDataSource {

    private final Path dbPath;
    private final String url;

    public LocalSQLiteDataSource() {
        MainAppConfig config = MainAppConfig.getInstance();
        this.dbPath = Paths.get(config.getSqliteDbPath()).toAbsolutePath().normalize();
        this.url = "jdbc:sqlite:" + dbPath;
        createParentDirectory();
        ensureSchema();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    // =========================================================
    // Schema initialisation
    // =========================================================

    /**
     * Creates all required tables if they do not already exist.
     * Safe to run for every data-source instance.
     */
    private void ensureSchema() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Enable WAL mode for better concurrent read performance
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");

            createCachedUsersTable(stmt);
            createUserSessionsTable(stmt);
            createCachedFilesTable(stmt);
            createCachedPermissionsTable(stmt);
            createPendingChangesTable(stmt);

            System.out.println("[LocalSQLiteDataSource] Schema initialised at: " + dbPath);

        } catch (SQLException e) {
            // Log but do not crash — the app can still run against MySQL remotely
            System.err.println("[LocalSQLiteDataSource] Schema initialisation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createParentDirectory() {
        Path parent = dbPath.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
                if (!Files.isWritable(parent)) {
                    throw new IOException("SQLite database directory is not writable");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to create writable SQLite database directory: " + parent, e);
        }
    }

    private static void createUserSessionsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS user_sessions (
                session_key   TEXT PRIMARY KEY,
                user_id       INTEGER NOT NULL,
                session_value TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES cached_users (user_id) ON DELETE CASCADE
            )
            """);
        stmt.execute("""
            CREATE INDEX IF NOT EXISTS idx_user_sessions_user
            ON user_sessions (user_id)
            """);
    }

    /**
     * Cached file metadata — mirrors the remote 'files' table.
     * sync_status: 'pending' | 'synced' | 'deleted'
     */
    private static void createCachedFilesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS cached_files (
                id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                file_id                 INTEGER UNIQUE,
                owner_id                INTEGER NOT NULL,
                filename                TEXT    NOT NULL,
                last_modified_server    TEXT,
                sync_status             TEXT    NOT NULL DEFAULT 'pending'
            )
            """);
        // Index speeds up the frequent owner_id + sync_status queries in SyncRepository
        stmt.execute("""
            CREATE INDEX IF NOT EXISTS idx_cached_files_owner
            ON cached_files (owner_id, sync_status)
            """);
    }

    /**
     * Cached user records — mirrors the remote 'users' table.
     * sync_status: 'pending' | 'synced' | 'updated' | 'deleted'
     */
    private static void createCachedUsersTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS cached_users (
                user_id                 INTEGER PRIMARY KEY,
                username                TEXT    NOT NULL UNIQUE,
                passwordhash            TEXT    NOT NULL,
                role                    TEXT    NOT NULL DEFAULT 'STANDARD',
                last_modified_server    TEXT,
                sync_status             TEXT    NOT NULL DEFAULT 'pending'
            )
            """);
    }

    /**
     * Cached file-permission records — mirrors the remote 'file_permissions' table.
     * sync_status: 'pending' | 'synced' | 'deleted'
     */
    private static void createCachedPermissionsTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS cached_permissions (
                id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                file_id                 INTEGER NOT NULL,
                user_id                 INTEGER NOT NULL,
                permission_type         TEXT    NOT NULL DEFAULT 'READ',
                last_modified_server    TEXT,
                sync_status             TEXT    NOT NULL DEFAULT 'pending',
                UNIQUE (file_id, user_id)
            )
            """);
    }

    /**
     * Pending change log — changes made offline that must be replayed to MySQL
     * when connectivity is restored.
     *
     * SyncService.loadPendingChangesOrdered() queries:
     *   SELECT change_id, target_table, target_id, change_type, payload
     *   FROM pending_changes ORDER BY created_date ASC
     *
     * SyncRepository.insertPendingChange() inserts:
     *   INSERT INTO pending_changes (target_table, target_id, change_type, payload)
     */
    private static void createPendingChangesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS pending_changes (
                change_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                target_table    TEXT    NOT NULL,
                target_id       INTEGER NOT NULL DEFAULT 0,
                change_type     TEXT    NOT NULL,
                payload         TEXT,
                created_date    TEXT    NOT NULL DEFAULT (datetime('now'))
            )
            """);
        stmt.execute("""
            CREATE INDEX IF NOT EXISTS idx_pending_changes_date
            ON pending_changes (created_date ASC)
            """);
    }
}
