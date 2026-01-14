package com.example.mainapp.service;

import com.example.mainapp.db.LocalSQLiteDataSource;
import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.logging.EventLogger;
import com.example.mainapp.logging.MysqlEventLogger;
import com.example.mainapp.model.User;
import com.example.mainapp.repository.SyncRepository;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SyncService {

    private final ConnectivityService connectivity;
    private final SyncAwareFileService fileService;
    private final SyncAwareUserService userService;
    private final SyncRepository syncRepository;
    private final LocalSQLiteDataSource localDataSource;
    private final EventLogger eventLogger;

    public SyncService(String brokerUrl, String mainAppId) throws Exception{
        this(
            new ConnectivityService(),
            new SyncAwareFileService(brokerUrl, mainAppId),  // CHANGE: Pass MQTT params
            new SyncAwareUserService(),
            new SyncRepository(),
            new LocalSQLiteDataSource(),
            new MysqlEventLogger(new RemoteMySQLDataSource())
        );
    }

    public SyncService(ConnectivityService connectivity,
                       SyncAwareFileService fileService,
                       SyncAwareUserService userService,
                       SyncRepository syncRepository,
                       LocalSQLiteDataSource localDataSource,
                       EventLogger eventLogger) {
        this.connectivity = connectivity;
        this.fileService = fileService;
        this.userService = userService;
        this.syncRepository = syncRepository;
        this.localDataSource = localDataSource;
        this.eventLogger = eventLogger;
    }

    /**
     * Call once after login, when connectivity.isOnline() is true.
     */
    public void runSync() {
        if (!connectivity.isOnline()) {
            return;
        }

        List<PendingChange> changes = loadPendingChangesOrdered();
        for (PendingChange change : changes) {
            try {
                switch (change.changeType) {
                    // Files – from SyncAwareFileService
                    case "FILE_CREATE" -> applyFileCreate(change);
                    case "FILE_RENAME" -> applyFileRename(change);
                    case "FILE_DELETE" -> applyFileDelete(change);

                    // Users – from SyncAwareUserService.register/delete/promote/password
                    case "USER_REGISTER" -> applyUserRegister(change);
                    case "USER_DELETE" -> applyUserDelete(change);
                    case "USER_ROLE_TOGGLE" -> applyUserRoleToggle(change);
                    case "USER_PASSWORD_UPDATE" -> applyUserPasswordUpdate(change);

                    // Permissions – from SyncAwareUserService.shareFile/revokeShare
                    case "PERMISSION_SHARE" -> applyPermissionShare(change);
                    case "PERMISSION_REVOKE" -> applyPermissionRevoke(change);

                    default -> eventLogger.logEvent(
                        null,
                        "SYNC_UNKNOWN_CHANGE",
                        "Unknown pending change type '" + change.changeType +
                            "' (id=" + change.changeId + ", table=" + change.targetTable + ")"
                    );
                }

                // if we reach here, either applied or conflict-handled: remove it
                deletePendingChange(change.changeId);

            } catch (Exception e) {
                System.err.println("SyncService error applying change " + change.changeId + ": " + e.getMessage());
                eventLogger.logEvent(
                    null,
                    "SYNC_FAILURE",
                    "Failed to apply pending change id=" + change.changeId +
                        " type=" + change.changeType + ". Reason: " + e.getMessage()
                );
                break; // stop; next login can retry remaining ones
            }
        }
    }

    // =========================================================
    // FILE_* handlers (payloads from SyncAwareFileService)
    // =========================================================

    // payload: { "ownerId": ..., "filename": "...", "size": ..., "localCachedId": ..., "localPath": "..." }
    private void applyFileCreate(PendingChange change) throws Exception {
        String json = change.payload;

        long ownerId = extractLong(json, "ownerId");
        String localPathStr = extractString(json, "localPath");
        String filename = extractString(json, "filename");
        long localCachedId = extractLong(json, "localCachedId");

        Path localPath = Path.of(localPathStr);

        try {
            var created = fileService.uploadFile(ownerId, localPath);

            syncRepository.upsertCachedFile(
                created.getId(),
                created.getOwnerId(),
                created.getFilename(),
                created.getLastModified(),
                "synced"
            );

            try {
                java.nio.file.Files.deleteIfExists(localPath);
            } catch (Exception ex) {
                System.err.println("Failed to delete offline copy " + localPath + ": " + ex.getMessage());
            }

            eventLogger.logEvent(
                ownerId,
                "SYNC_FILE_CREATE",
                "Synced offline-created file '" + filename + "' as ID=" + created.getId() +
                    " (localCachedId=" + localCachedId + ")."
            );
        } catch (IllegalArgumentException ex) {
            // e.g. file already exists or invalid; treat as conflict
            eventLogger.logEvent(
                ownerId,
                "SYNC_CONFLICT_FILE_CREATE",
                "Skipped FILE_CREATE for '" + filename + "' (localCachedId=" + localCachedId +
                    ") because: " + ex.getMessage()
            );
        } catch (Exception ex){
            eventLogger.logEvent(
                ownerId,
                "SYNC_ERROR_FILE_CREATE",
                "Skipped FILE_CREATE for '" + filename + "' (localCachedId=" + localCachedId +
                    ") because: " + ex.getMessage()
            );
        }
    }

    // payload: { "fileId": ..., "userId": ..., "isAdmin": true/false, "newName": "..." }
    private void applyFileRename(PendingChange change) throws Exception {
        String json = change.payload;

        long fileId = extractLong(json, "fileId");
        long userId = extractLong(json, "userId");
        boolean isAdmin = extractBoolean(json, "isAdmin");
        String newName = extractString(json, "newName");

        try {
            var updated = fileService.renameFile(fileId, userId, isAdmin, newName);

            syncRepository.upsertCachedFile(
                updated.getId(),
                updated.getOwnerId(),
                updated.getFilename(),
                updated.getLastModified(),
                "synced"
            );

            eventLogger.logEvent(
                userId,
                "SYNC_FILE_RENAME",
                "Synced offline rename for file ID=" + fileId + " to '" + newName + "'."
            );
        } catch (IllegalArgumentException ex) {
            // typical case: file already deleted or user has no rights now
            eventLogger.logEvent(
                userId,
                "SYNC_CONFLICT_FILE_RENAME",
                "Skipped FILE_RENAME for file ID=" + fileId +
                    " because: " + ex.getMessage()
            );
        } catch (Exception ex){
            eventLogger.logEvent(
                userId,
                "SYNC_ERROR_FILE_RENAME",
                "Skipped FILE_RENAME for file ID=" + fileId +
                    " because: " + ex.getMessage()
            );
            throw ex;
        } 
    }

    // payload: { "fileId": ..., "userId": ..., "isAdmin": true/false }
    private void applyFileDelete(PendingChange change) throws Exception {
        String json = change.payload;

        long fileId = extractLong(json, "fileId");
        long userId = extractLong(json, "userId");
        boolean isAdmin = extractBoolean(json, "isAdmin");

        try {
            fileService.deleteFile(fileId, userId, isAdmin);
            syncRepository.markCachedFileDeleted(fileId);
            eventLogger.logEvent(
                userId,
                "SYNC_FILE_DELETE",
                "Synced offline delete for file ID=" + fileId + "."
            );
        } catch (IllegalArgumentException ex) {
            // file may already be deleted; treat as “already applied”
            syncRepository.markCachedFileDeleted(fileId);
            eventLogger.logEvent(
                userId,
                "SYNC_CONFLICT_FILE_DELETE",
                "Skipped FILE_DELETE for file ID=" + fileId +
                    " because: " + ex.getMessage()
            );
        } catch (Exception ex) {  // CHANGE 3: ADD: Catch other exceptions (MQTT/SFTP failures)
            // MQTT or SFTP errors should propagate to stop sync
            eventLogger.logEvent(
                userId,
                "SYNC_ERROR_FILE_DELETE",
                "Failed to sync delete for file ID=" + fileId + ": " + ex.getMessage()
            );
            throw ex;  // Re-throw to stop sync process
        }
    }

    // =========================================================
    // USER_* handlers (payloads from SyncAwareUserService)
    // =========================================================

    // payload: { "username": "...", "passwordHash": "...", "role": "STANDARD/ADMIN", "actorId": null|<id> }
    private void applyUserRegister(PendingChange change) {
        String json = change.payload;

        String username = extractString(json, "username");
        String passwordHash = extractString(json, "passwordHash");
        String roleStr = extractString(json, "role");
        String actorIdStr = extractRaw(json, "actorId"); // may be null
        Long actorId = (actorIdStr == null || actorIdStr.equals("null"))
            ? null
            : Long.parseLong(actorIdStr);

        try {
            // NOTE: register expects plain password; here we only have hash.
            // Simplest: treat duplicates as conflict; registrationService in online path
            // will rehash anyway – you accepted that for sync.
            User.Role role = User.Role.valueOf(roleStr);
            var created = userService.register(username, passwordHash, role, actorId);

            eventLogger.logEvent(
                actorId != null ? actorId : created.getId(),
                "SYNC_USER_REGISTER",
                "Synced offline registration for '" + username + "' as ID=" + created.getId() + "."
            );
        } catch (IllegalArgumentException ex) {
            // main conflict: username already exists online with different data
            eventLogger.logEvent(
                actorId,
                "SYNC_CONFLICT_USER_REGISTER",
                "Skipped USER_REGISTER for '" + username + "' because: " + ex.getMessage()
            );
        }
    }

    // payload: { "userId": ... }
    private void applyUserDelete(PendingChange change) {
        String json = change.payload;

        long userId = extractLong(json, "userId");

        try {
            userService.deleteUser(userId);
            eventLogger.logEvent(
                null,
                "SYNC_USER_DELETE",
                "Synced offline delete for user ID=" + userId + "."
            );
        } catch (IllegalArgumentException ex) {
            // user may already be deleted
            syncRepository.markCachedUserDeleted(userId);
            eventLogger.logEvent(
                null,
                "SYNC_CONFLICT_USER_DELETE",
                "Skipped USER_DELETE for user ID=" + userId +
                    " because: " + ex.getMessage()
            );
        }
    }

    // payload: { "userId": ... }
    private void applyUserRoleToggle(PendingChange change) {
        String json = change.payload;

        long userId = extractLong(json, "userId");

        try {
            userService.promoteDemoteUser(userId);
            eventLogger.logEvent(
                null,
                "SYNC_USER_ROLE_TOGGLE",
                "Synced offline role toggle for user ID=" + userId + "."
            );
        } catch (IllegalArgumentException ex) {
            // user may already be deleted
            eventLogger.logEvent(
                null,
                "SYNC_CONFLICT_USER_ROLE_TOGGLE",
                "Skipped USER_ROLE_TOGGLE for user ID=" + userId +
                    " because: " + ex.getMessage()
            );
        }
    }

    // payload: { "username": "...", "passwordHash": "..." }
    private void applyUserPasswordUpdate(PendingChange change) {
        String json = change.payload;

        String username = extractString(json, "username");
        String passwordHash = extractString(json, "passwordHash");

        try {
            userService.updatePassword(username, passwordHash);
            eventLogger.logEvent(
                null,
                "SYNC_USER_PASSWORD_UPDATE",
                "Synced offline password update for user '" + username + "'."
            );
        } catch (IllegalArgumentException ex) {
            // user may have been deleted
            eventLogger.logEvent(
                null,
                "SYNC_CONFLICT_USER_PASSWORD_UPDATE",
                "Skipped USER_PASSWORD_UPDATE for '" + username +
                    "' because: " + ex.getMessage()
            );
        }
    }

    // =========================================================
    // PERMISSION_* handlers (payloads from SyncAwareUserService)
    // =========================================================

    // payload: { "fileId": ..., "userId": ..., "permissionType": "READ/READ_WRITE" }
    private void applyPermissionShare(PendingChange change) {
        String json = change.payload;

        long fileId = extractLong(json, "fileId");
        long targetUserId = extractLong(json, "userId");
        String permissionType = extractString(json, "permissionType");

        try {
            userService.shareFile(fileId, targetUserId, permissionType);
            eventLogger.logEvent(
                null,
                "SYNC_PERMISSION_SHARE",
                "Synced offline share of file ID=" + fileId +
                    " with user ID=" + targetUserId + " as " + permissionType + "."
            );
        } catch (IllegalArgumentException ex) {
            // file or user might be gone, or permission already exists
            eventLogger.logEvent(
                null,
                "SYNC_CONFLICT_PERMISSION_SHARE",
                "Skipped PERMISSION_SHARE for file ID=" + fileId +
                    ", user ID=" + targetUserId + " because: " + ex.getMessage()
            );
        }
    }

    // payload: { "fileId": ..., "userId": ... }
    private void applyPermissionRevoke(PendingChange change) {
        String json = change.payload;

        long fileId = extractLong(json, "fileId");
        long targetUserId = extractLong(json, "userId");

        try {
            userService.revokeShare(fileId, targetUserId);
            eventLogger.logEvent(
                null,
                "SYNC_PERMISSION_REVOKE",
                "Synced offline revoke of permissions on file ID=" + fileId +
                    " for user ID=" + targetUserId + "."
            );
        } catch (IllegalArgumentException ex) {
            // file, user, or permission might already be removed
            eventLogger.logEvent(
                null,
                "SYNC_CONFLICT_PERMISSION_REVOKE",
                "Skipped PERMISSION_REVOKE for file ID=" + fileId +
                    ", user ID=" + targetUserId + " because: " + ex.getMessage()
            );
        }
    }

    // =========================================================
    // pending_changes helpers
    // =========================================================

    private List<PendingChange> loadPendingChangesOrdered() {
        String sql = """
            SELECT change_id, target_table, target_id, change_type, payload
            FROM pending_changes
            ORDER BY created_date ASC
            """;

        List<PendingChange> list = new ArrayList<>();

        try (Connection conn = localDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                PendingChange c = new PendingChange();
                c.changeId = rs.getLong("change_id");
                c.targetTable = rs.getString("target_table");
                c.changeType = rs.getString("change_type");
                c.payload = rs.getString("payload");
                list.add(c);
            }
        } catch (SQLException e) {
            System.err.println("SyncService.loadPendingChangesOrdered error: " + e.getMessage());
        }

        return list;
    }

    private void deletePendingChange(long changeId) {
        String sql = "DELETE FROM pending_changes WHERE change_id = ?";

        try (Connection conn = localDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, changeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SyncService.deletePendingChange error: " + e.getMessage());
        }
    }

    // =========================================================
    // Tiny JSON helpers (match what you already used)
    // =========================================================

    private long extractLong(String json, String field) {
        String v = extractString(json, field);
        return Long.parseLong(v);
    }

    private String extractRaw(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx == -1) return null;
        int start = idx + key.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && !",}".contains(String.valueOf(json.charAt(end)))) {
            end++;
        }
        return json.substring(start, end).trim();
    }

    private boolean extractBoolean(String json, String field) {
        String raw = extractRaw(json, field);
        return raw != null && raw.equalsIgnoreCase("true");
    }

    private String extractString(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx == -1) return "";
        int start = idx + key.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start < json.length() && json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && "0123456789".indexOf(json.charAt(end)) >= 0) {
                end++;
            }
            return json.substring(start, end);
        }
    }

    /**
     * Cleanup method to shutdown all services
     */
    public void shutdown() {
        if (fileService != null) {
            fileService.shutdown();
        }
        // Add other cleanup as needed
    }

    private static class PendingChange {
        long changeId;
        String targetTable;
        String changeType;
        String payload;
    }
}
