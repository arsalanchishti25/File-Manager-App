package com.example.mainapp.repository;

import com.example.mainapp.db.LocalSQLiteDataSource;
import com.example.mainapp.model.File;
import com.example.mainapp.model.User;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SyncRepository {

    private final LocalSQLiteDataSource dataSource;

    // Where offline file copies are stored (already used for files)
    private static final Path OFFLINE_BASE = Paths.get("data", "offline");

    public SyncRepository() {
        this.dataSource = new LocalSQLiteDataSource();
    }

    public SyncRepository(LocalSQLiteDataSource dataSource) {
        this.dataSource = dataSource;
    }

    // =========================================================
    // cached_files (existing)
    // =========================================================

    public void upsertCachedFile(long fileId,
                                 long ownerId,
                                 String filename,
                                 LocalDateTime lastModifiedServer,
                                 String syncStatus) {
        String sql = """
            INSERT INTO cached_files (file_id, owner_id, filename, last_modified_server, sync_status)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(file_id) DO UPDATE SET
                owner_id = excluded.owner_id,
                filename = excluded.filename,
                last_modified_server = excluded.last_modified_server,
                sync_status = excluded.sync_status
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            ps.setLong(2, ownerId);
            ps.setString(3, filename);
            if (lastModifiedServer != null) {
                ps.setObject(4, lastModifiedServer);
            } else {
                ps.setNull(4, Types.TIMESTAMP);
            }
            ps.setString(5, syncStatus);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.upsertCachedFile error: " + e.getMessage());
        }
    }

    public long insertLocalCachedFile(long ownerId,
                                      String filename,
                                      LocalDateTime lastModifiedServer,
                                      String syncStatus) {
        String sql = """
            INSERT INTO cached_files (file_id, owner_id, filename, last_modified_server, sync_status)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setNull(1, Types.BIGINT);
            ps.setLong(2, ownerId);
            ps.setString(3, filename);
            if (lastModifiedServer != null) {
                ps.setObject(4, lastModifiedServer);
            } else {
                ps.setNull(4, Types.TIMESTAMP);
            }
            ps.setString(5, syncStatus);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Insert local cached file failed, no rows affected.");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1); // cached_files.id
                }
            }
            throw new SQLException("Insert local cached file failed, no ID obtained.");
        } catch (SQLException e) {
            System.err.println("SyncRepository.insertLocalCachedFile error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void updateCachedFileName(long cachedIdOrFileId,
                                     String newName,
                                     LocalDateTime modifiedLocal) {
        String sql = """
            UPDATE cached_files
            SET filename = ?, last_modified_server = ?
            WHERE id = ? OR file_id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newName);
            ps.setObject(2, modifiedLocal);
            ps.setLong(3, cachedIdOrFileId);
            ps.setLong(4, cachedIdOrFileId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.updateCachedFileName error: " + e.getMessage());
        }
    }

    public void markCachedFileDeleted(long cachedIdOrFileId) {
        String sql = """
            UPDATE cached_files
            SET sync_status = 'deleted'
            WHERE id = ? OR file_id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, cachedIdOrFileId);
            ps.setLong(2, cachedIdOrFileId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.markCachedFileDeleted error: " + e.getMessage());
        }
    }

    public List<File> loadCachedFilesForUser(long userId, boolean isAdmin) {
        String sql;
        if (isAdmin) {
            sql = """
                SELECT file_id, owner_id, filename, last_modified_server
                FROM cached_files
                WHERE sync_status != 'deleted'
                """;
        } else {
            sql = """
                SELECT file_id, owner_id, filename, last_modified_server
                FROM cached_files
                WHERE owner_id = ? AND sync_status != 'deleted'
                """;
        }

        List<File> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (!isAdmin) {
                ps.setLong(1, userId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long fileId = rs.getLong("file_id");
                    long ownerId = rs.getLong("owner_id");
                    String filename = rs.getString("filename");
                    LocalDateTime lastMod = rs.getObject("last_modified_server", LocalDateTime.class);

                    result.add(new File(
                        fileId,
                        ownerId,
                        filename,
                        0L,
                        lastMod != null ? lastMod : LocalDateTime.now(),
                        lastMod != null ? lastMod : LocalDateTime.now()
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("SyncRepository.loadCachedFilesForUser error: " + e.getMessage());
        }

        return result;
    }

    public Path getOfflineCopyPath(long localCachedId, String originalFilename) {
        String safeName = originalFilename == null ? "file" : originalFilename;
        return OFFLINE_BASE.resolve(localCachedId + "_" + safeName);
    }

    // =========================================================
    // cached_users
    // =========================================================

    public void upsertCachedUser(long userId,
                                 String username,
                                 String passwordHash,
                                 String role,
                                 LocalDateTime lastModifiedServer,
                                 String syncStatus) {
        String sql = """
            INSERT INTO cached_users (user_id, username, passwordhash, role, last_modified_server, sync_status)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                username = excluded.username,
                passwordhash = excluded.passwordhash,
                role = excluded.role,
                last_modified_server = excluded.last_modified_server,
                sync_status = excluded.sync_status
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setString(2, username);
            ps.setString(3, passwordHash);
            ps.setString(4, role);
            if (lastModifiedServer != null) {
                ps.setObject(5, lastModifiedServer);
            } else {
                ps.setNull(5, Types.TIMESTAMP);
            }
            ps.setString(6, syncStatus);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.upsertCachedUser error: " + e.getMessage());
        }
    }

    public Optional<User> findCachedUserByUsername(String username) {
        String sql = """
            SELECT user_id, username, passwordhash, role, last_modified_server
            FROM cached_users
            WHERE username = ?
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long userId = rs.getLong("user_id");
                    String uname = rs.getString("username");
                    String hash = rs.getString("passwordhash");
                    String roleStr = rs.getString("role");
                    LocalDateTime createdAt =
                        rs.getObject("last_modified_server", LocalDateTime.class);
                    if (createdAt == null) {
                        createdAt = LocalDateTime.now();
                    }
                    User.Role role = User.Role.valueOf(roleStr);

                    return Optional.of(new User(userId, uname, hash, role, createdAt));
                }
            }
        } catch (SQLException e) {
            System.err.println("SyncRepository.findCachedUserByUsername error: " + e.getMessage());
        }

        return Optional.empty();
    }

    public void updateCachedUserPassword(String username, String newHash) {
        String sql = """
            UPDATE cached_users
            SET passwordhash = ?, sync_status = 'updated'
            WHERE username = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newHash);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.updateCachedUserPassword error: " + e.getMessage());
        }
    }

    public void markCachedUserDeleted(long userId) {
        String sql = """
            UPDATE cached_users
            SET sync_status = 'deleted'
            WHERE user_id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.markCachedUserDeleted error: " + e.getMessage());
        }
    }

    public void toggleCachedUserRole(long userId) {
        String sql = """
            UPDATE cached_users
            SET role = CASE
                           WHEN role = 'STANDARD' THEN 'ADMIN'
                           WHEN role = 'ADMIN' THEN 'STANDARD'
                           ELSE role
                       END,
                sync_status = 'updated'
            WHERE user_id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.toggleCachedUserRole error: " + e.getMessage());
        }
    }

    // =========================================================
    // cached_permissions
    // =========================================================

    public void upsertCachedPermission(long fileId,
                                       long userId,
                                       String permissionType,
                                       LocalDateTime lastModifiedServer,
                                       String syncStatus) {
        String sql = """
            INSERT INTO cached_permissions (file_id, user_id, permission_type, last_modified_server, sync_status)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(file_id, user_id) DO UPDATE SET
                permission_type = excluded.permission_type,
                last_modified_server = excluded.last_modified_server,
                sync_status = excluded.sync_status
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            ps.setLong(2, userId);
            ps.setString(3, permissionType);
            if (lastModifiedServer != null) {
                ps.setObject(4, lastModifiedServer);
            } else {
                ps.setNull(4, Types.TIMESTAMP);
            }
            ps.setString(5, syncStatus);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.upsertCachedPermission error: " + e.getMessage());
        }
    }

    public void deleteCachedPermission(long fileId, long userId) {
        String sql = """
            DELETE FROM cached_permissions
            WHERE file_id = ? AND user_id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, fileId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.deleteCachedPermission error: " + e.getMessage());
        }
    }

    public Optional<String> findCachedPermissionForUserAndFile(long userId, long fileId) {
        String sql = """
            SELECT permission_type
            FROM cached_permissions
            WHERE user_id = ? AND file_id = ?
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setLong(2, fileId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("permission_type"));
                }
            }
        } catch (SQLException e) {
            System.err.println("SyncRepository.findCachedPermissionForUserAndFile error: " + e.getMessage());
        }

        return Optional.empty();
    }

    public List<Long> findCachedFileIdsSharedWithUser(long userId) {
        String sql = """
            SELECT file_id
            FROM cached_permissions
            WHERE user_id = ?
            """;

        List<Long> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("file_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("SyncRepository.findCachedFileIdsSharedWithUser error: " + e.getMessage());
        }

        return result;
    }

    // =========================================================
    // pending_changes (existing)
    // =========================================================

    public void insertPendingChange(String targetTable,
                                    long targetId,
                                    String changeType,
                                    String payloadJson) {
        String sql = """
            INSERT INTO pending_changes (target_table, target_id, change_type, payload)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, targetTable);
            ps.setLong(2, targetId);
            ps.setString(3, changeType);
            ps.setString(4, payloadJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncRepository.insertPendingChange error: " + e.getMessage());
        }
    }

}
