package com.example.mainapp.db;

import com.example.mainapp.config.MainAppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

    // Path is relative to the working directory of the running process.
    // In Docker Compose the working dir is /app/data (mounted volume) so
    // the DB persists across restarts.  Locally it falls back to the project root.
    private final String dbPath;
    private final String url;

    /** Tracks whether schema init has run in this JVM process. */
    private static volatile boolean schemaInitialised = false;

    public LocalSQLiteDataSource() {
        MainAppConfig config = MainAppConfig.getInstance();
        this.dbPath = config.getSqliteDbPath();
        this.url = "jdbc:sqlite:" + dbPath;
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
     * Called once per JVM startup (double-checked locking on the flag).
     */
    private synchronized void ensureSchema() {
        if (schemaInitialised) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // Enable WAL mode for better concurrent read performance
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");

            createCachedFilesTable(stmt);
            createCachedUsersTable(stmt);
            createCachedPermissionsTable(stmt);
            createPendingChangesTable(stmt);

            schemaInitialised = true;
            System.out.println("[LocalSQLiteDataSource] Schema initialised at: " + dbPath);

        } catch (SQLException e) {
            // Log but do not crash — the app can still run against MySQL remotely
            System.err.println("[LocalSQLiteDataSource] Schema initialisation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cached file metadata — mirrors the remote 'files' table.
     * sync_status: 'pending' | 'synced' | 'deleted'
     */
    private static void createCachedFilesTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS cached_files (
                id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                file_id                 INTEGER NOT NULL DEFAULT 0,
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
                id                      INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id                 INTEGER NOT NULL UNIQUE,
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
