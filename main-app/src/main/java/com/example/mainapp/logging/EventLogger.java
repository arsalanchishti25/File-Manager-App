// src/main/java/com/example/filemanager/logging/EventLogger.java
package com.example.mainapp.logging;

public interface EventLogger {

    // Core generic method
    void logEvent(Long userId, String eventType, String description);

    // Convenience methods

    // Auth
    void logLoginSuccess(long userId, String username);
    void logLoginFailure(String username, String reason);

    // User management
    void logUserRegistered(long adminId, long newUserId, String newUsername);
    void logUserDeleted(long adminId, long deletedUserId, String deletedUsername);
    void logUserRoleChanged(long adminId, long userId, String username,
                            String oldRole, String newRole);
    void logPasswordChanged(long userId, String username);

    // Files
    void logFileUploaded(long userId, long fileId, String filename, long sizeBytes);
    void logFileDeleted(long userId, long fileId, String filename);
    void logFileRenamed(long userId, long fileId,
                        String oldName, String newName);
    void logFileShared(long ownerId, long fileId, String filename,
                       long targetUserId, String targetUsername, String permissionType);
    void logFileEdited(long userId, long fileId, String filename);

}
