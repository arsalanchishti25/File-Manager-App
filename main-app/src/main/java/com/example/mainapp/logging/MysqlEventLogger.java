// src/main/java/com/example/filemanager/logging/MysqlEventLogger.java
package com.example.mainapp.logging;

import com.example.mainapp.db.RemoteMySQLDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MysqlEventLogger implements EventLogger {

    private final RemoteMySQLDataSource dataSource;

    public MysqlEventLogger(RemoteMySQLDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void logEvent(Long userId, String eventType, String description) {
        String sql = "INSERT INTO event_logs (user_id, event_type, description) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();

             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (userId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setLong(1, userId);
            }
            ps.setString(2, eventType);
            ps.setString(3, description);

            ps.executeUpdate();

        } catch (SQLException e) {
            // As a fallback, you can still print to console
            System.err.println("Event logging failed: " + e.getMessage());
        }
    }

    // ===== Convenience methods =====

    @Override
    public void logLoginSuccess(long userId, String username) {
        logEvent(userId, "LOGIN_SUCCESS", "User '" + username + "' logged in successfully.");
    }

    @Override
    public void logLoginFailure(String username, String reason) {
        logEvent(null, "LOGIN_FAILURE",
                "Login failed for username '" + username + "'. Reason: " + reason);
    }

    @Override
    public void logUserRegistered(long adminId, long newUserId, String newUsername) {
        logEvent(adminId, "USER_REGISTERED",
                "Admin created user '" + newUsername + "' (ID=" + newUserId + ").");
    }

    @Override
    public void logUserDeleted(long adminId, long deletedUserId, String deletedUsername) {
        logEvent(adminId, "USER_DELETED",
                "Admin deleted user '" + deletedUsername + "' (ID=" + deletedUserId + ").");
    }

    @Override
    public void logUserRoleChanged(long adminId, long userId, String username,
                                   String oldRole, String newRole) {
        logEvent(adminId, "USER_ROLE_CHANGED",
                "Admin changed role of user '" + username + "' (ID=" + userId +
                        ") from " + oldRole + " to " + newRole + ".");
    }

    @Override
    public void logPasswordChanged(long userId, String username) {
        logEvent(userId, "PASSWORD_CHANGED",
                "User '" + username + "' changed password.");
    }

    @Override
    public void logFileUploaded(long userId, long fileId, String filename, long sizeBytes) {
        logEvent(userId, "FILE_UPLOADED",
                "Uploaded file '" + filename + "' (ID=" + fileId +
                        ", size=" + sizeBytes + " bytes).");
    }

    @Override
    public void logFileDeleted(long userId, long fileId, String filename) {
        logEvent(userId, "FILE_DELETED",
                "Deleted file '" + filename + "' (ID=" + fileId + ").");
    }

    @Override
    public void logFileRenamed(long userId, long fileId,
                               String oldName, String newName) {
        logEvent(userId, "FILE_RENAMED",
                "Renamed file ID=" + fileId + " from '" + oldName +
                        "' to '" + newName + "'.");
    }

    @Override
    public void logFileShared(long ownerId, long fileId, String filename,
                              long targetUserId, String targetUsername, String permissionType) {
        logEvent(ownerId, "FILE_SHARED",
                "Shared file '" + filename + "' (ID=" + fileId +
                        ") with user '" + targetUsername + "' (ID=" + targetUserId +
                        ") as " + permissionType + ".");
    }

    @Override
    public void logFileEdited(long userId, long fileId, String filename) {
        logEvent(userId, "FILE_EDITED",
                "User edited file '" + filename + "' (ID=" + fileId + ").");
    }
    
}
