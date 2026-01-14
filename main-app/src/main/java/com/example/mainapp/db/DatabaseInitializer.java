package com.example.mainapp.db;

import com.example.mainapp.util.PasswordHasher;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    public static void initializeLocalDatabase() {
        LocalSQLiteDataSource dataSource = new LocalSQLiteDataSource();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // User sessions (for offline/multi-window support)
            String createSessionsTable = "CREATE TABLE IF NOT EXISTS user_sessions (" +
                    "session_key TEXT PRIMARY KEY," +
                    "user_id INTEGER NOT NULL," +
                    "session_value TEXT NOT NULL," +
                    "FOREIGN KEY(user_id) REFERENCES users(id)" +
                    ")";
            stmt.execute(createSessionsTable);

            // Cached files metadata
            String createCachedFilesTable = "CREATE TABLE IF NOT EXISTS cached_files (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "file_id INTEGER NOT NULL," +
                    "owner_id INTEGER NOT NULL," +
                    "filename TEXT NOT NULL," +
                    "last_modified_server DATETIME," +
                    "sync_status TEXT DEFAULT 'new'," +
                    "FOREIGN KEY(owner_id) REFERENCES users(id)" +
                    ")";
            stmt.execute(createCachedFilesTable);

            String createCachedUsers = """
                CREATE TABLE IF NOT EXISTS cached_users (
                        user_id     INTEGER PRIMARY KEY,   -- MySQL users.id
                        username    TEXT NOT NULL UNIQUE,
                        passwordhash TEXT NOT NULL,
                        role        TEXT NOT NULL,
                        last_modified_server DATETIME,
                        sync_status TEXT NOT NULL
                )
                """;
            stmt.execute(createCachedUsers);

            // Pending changes for offline sync


            /*
            ****chaneg the nname of this table specially.
            */
            String createPendingChangesTable = "CREATE TABLE IF NOT EXISTS pending_changes (" +
                    "change_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "target_table TEXT NOT NULL," +
                    "target_id INTEGER NOT NULL," +
                    "created_date DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "change_type TEXT NOT NULL," +
                    "payload TEXT NOT NULL" +
                    ")";
            stmt.execute(createPendingChangesTable);

            System.out.println("✓ Local SQLite database initialized.");

        } catch (SQLException e) {
            System.err.println("✗ Failed to initialize local database:");
            e.printStackTrace();
        }
    }

    public static void initializeRemoteDatabase() {
        RemoteMySQLDataSource dataSource = new RemoteMySQLDataSource();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Users table (remote)
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(255) NOT NULL UNIQUE," +
                    "password_hash VARCHAR(255) NOT NULL," +
                    "role VARCHAR(50) NOT NULL DEFAULT 'STANDARD'," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.execute(createUsersTable);

            // Files table (remote)
            String createFilesTable = "CREATE TABLE IF NOT EXISTS files (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "owner_id INT NOT NULL," +
                    "filename VARCHAR(255) NOT NULL," +
                    "size_bytes BIGINT NOT NULL," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "last_modified DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(owner_id) REFERENCES users(id)" +
                    ")";
            stmt.execute(createFilesTable);

            // File chunks table (for chunked uploads)
            String createFileChunksTable = "CREATE TABLE IF NOT EXISTS file_chunks (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "file_id INT NOT NULL," +
                    "chunk_order INT NOT NULL," +
                    "storage_location VARCHAR(255)," +
                    "crc32_checksum VARCHAR(255)," +
                    "volume_group VARCHAR(100)," +
                    "FOREIGN KEY(file_id) REFERENCES files(id)" +
                    ")";
            stmt.execute(createFileChunksTable);

            // File permissions table
            String createPermissionsTable = "CREATE TABLE IF NOT EXISTS file_permissions (" +
                    "permission_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "file_id INT NOT NULL," +
                    "user_id INT NOT NULL," +
                    "permission_type VARCHAR(50) NOT NULL," +
                    "FOREIGN KEY(file_id) REFERENCES files(id)," +
                    "FOREIGN KEY(user_id) REFERENCES users(id)" +
                    ")";
            stmt.execute(createPermissionsTable);

            // Event logs table
            String createEventLogsTable = "CREATE TABLE IF NOT EXISTS event_logs (" +
                    "log_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id INT," +
                    "event_type VARCHAR(100)," +
                    "description TEXT," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(user_id) REFERENCES users(id)" +
                    ")";
            stmt.execute(createEventLogsTable);

            // Containers table (for phase 5)
            String createContainersTable = "CREATE TABLE IF NOT EXISTS containers (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(255) NOT NULL," +
                    "container_ip VARCHAR(45)," +
                    "status VARCHAR(50)," +
                    "created_date DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "last_health_check DATETIME," +
                    "load_score INT DEFAULT 0," +
                    "container_type VARCHAR(50)," +
                    "volume_group VARCHAR(100)," +
                    "assigned_port INT" +
                    ")";
            stmt.execute(createContainersTable);

            // Insert test users (standard + admin)
            String testhashedPassword = PasswordHasher.hash("test");
            String adminhashedPassword = PasswordHasher.hash("admin");

            String insertTestUser =
                "INSERT INTO users (username, password_hash, role) " +
                "VALUES ('test', '" + testhashedPassword + "', 'STANDARD') " +
                "ON DUPLICATE KEY UPDATE username = VALUES(username)";
            stmt.execute(insertTestUser);

            String insertAdminUser =
                "INSERT INTO users (username, password_hash, role) " +
                "VALUES ('admin', '" + adminhashedPassword + "', 'ADMIN') " +
                "ON DUPLICATE KEY UPDATE username = VALUES(username)";
            stmt.execute(insertAdminUser);

            System.out.println("✓ Remote MySQL database initialized.");

        } catch (SQLException e) {
            System.err.println("✗ Failed to initialize remote database:");
            e.printStackTrace();
        }
    }
}
