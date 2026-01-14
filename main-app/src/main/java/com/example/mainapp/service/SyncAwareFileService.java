// src/main/java/com/example/mainapp/service/SyncAwareFileService.java
package com.example.mainapp.service;

import com.example.mainapp.model.File;
import com.example.mainapp.repository.SyncRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Wrapper around FileService that:
 * - Uses ConnectivityService to decide online/offline
 * - Updates local SQLite cache (cached_files)
 * - Enqueues pending_changes when offline or MySQL fails
 * 
 * CHANGE: Updated to work with new FileService constructor that requires MQTT
 */
public class SyncAwareFileService {

    private final FileService fileService;
    private final ConnectivityService connectivityService;
    private final SyncRepository syncRepository;

    /**
     * CHANGE 1: Updated default constructor to accept MQTT broker and app ID
     * WHY: FileService now requires these parameters
     * 
     * @param brokerUrl MQTT broker URL (e.g., "tcp://localhost:1883")
     * @param mainAppId Unique ID for this main app instance (e.g., "main-app-1")
     */
    public SyncAwareFileService(String brokerUrl, String mainAppId) throws Exception {
        this(
            new FileService(brokerUrl, mainAppId),  // CHANGE: Pass MQTT params
            new ConnectivityService(),
            new SyncRepository()
        );
    }

    /**
     * CHANGE 2: Constructor for dependency injection (testing)
     * NO CHANGE: Still accepts pre-configured instances
     */
    public SyncAwareFileService(FileService fileService,
                                ConnectivityService connectivityService,
                                SyncRepository syncRepository) {
        this.fileService = fileService;
        this.connectivityService = connectivityService;
        this.syncRepository = syncRepository;
    }

    // ---------- UPLOAD ----------
    
    /**
     * Upload a file with online/offline handling.
     * NO LOGIC CHANGES: Just propagates to FileService (which now uses MQTT internally)
     */
    public File uploadFile(long ownerId, Path sourcePath) throws Exception {
        boolean online = connectivityService.isOnline();

        if (online) {
            try {
                // CHANGE 3: FileService.uploadFile now uses MQTT/SFTP internally
                // We don't need to change the call - it's the same signature
                File file = fileService.uploadFile(ownerId, sourcePath);
                
                syncRepository.upsertCachedFile(
                    file.getId(),
                    file.getOwnerId(),
                    file.getFilename(),
                    file.getLastModified(),
                    "synced"
                );
                return file;
            } catch (Exception ex) {
                if (isSqlConnectionError(ex)) {
                    connectivityService.markOfflineOnSQLException((SQLException) ex.getCause());
                    return uploadFileOffline(ownerId, sourcePath);
                }
                throw ex;
            }
        } else {
            return uploadFileOffline(ownerId, sourcePath);
        }
    }

    /**
     * Offline upload: Store file locally and queue for later sync.
     * NO CHANGES: Offline logic remains the same
     */
    private File uploadFileOffline(long ownerId, Path sourcePath) throws Exception {
        String filename = sourcePath.getFileName().toString();
        long size = Files.size(sourcePath);

        // 1) Create local-only stub in cached_files
        long tempId = syncRepository.insertLocalCachedFile(
            ownerId,
            filename,
            null,
            "new"
        );

        // 2) Copy the file to a stable offline location
        Path offlinePath = syncRepository.getOfflineCopyPath(tempId, filename);
        Files.createDirectories(offlinePath.getParent());
        Files.copy(sourcePath, offlinePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // 3) Enqueue pending FILE_CREATE with localPath
        String payloadJson = String.format(
            "{ \"ownerId\": %d, \"filename\": \"%s\", \"size\": %d, \"localCachedId\": %d, \"localPath\": \"%s\" }",
            ownerId, filename, size, tempId, offlinePath.toAbsolutePath().toString().replace("\\", "\\\\")
        );

        syncRepository.insertPendingChange(
            "files",
            tempId,
            "FILE_CREATE",
            payloadJson
        );

        // 4) Return pseudo-File for UI
        return new File(
            tempId,
            ownerId,
            filename,
            size,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
    
    // ---------- DELETE ----------

    /**
     * Delete a file with online/offline handling.
     * 
     * CHANGE 4: FileService.deleteFile signature changed to throw Exception
     * WHY: Now involves MQTT operations which can throw exceptions
     */
    public void deleteFile(long fileId, long userId, boolean isAdmin) throws Exception {
        boolean online = connectivityService.isOnline();

        if (online) {
            try {
                // CHANGE 5: deleteFile now throws Exception instead of RuntimeException
                fileService.deleteFile(fileId, userId, isAdmin);
                
                // Mark cache as deleted / remove row
                syncRepository.markCachedFileDeleted(fileId);
                return;
            } catch (Exception ex) {  // CHANGE 6: Catch Exception instead of RuntimeException
                if (isSqlConnectionError(ex)) {
                    connectivityService.markOfflineOnSQLException((SQLException) ex.getCause());
                    deleteFileOffline(fileId, userId, isAdmin);
                    return;
                }
                throw ex;
            }
        } else {
            deleteFileOffline(fileId, userId, isAdmin);
        }
    }

    /**
     * Offline delete: Mark as deleted locally and queue for later sync.
     * NO CHANGES: Offline logic remains the same
     */
    private void deleteFileOffline(long fileId, long userId, boolean isAdmin) {
        // Locally mark as deleted in cache
        syncRepository.markCachedFileDeleted(fileId);

        // Queue a delete for when online
        String payloadJson = String.format(
            "{ \"fileId\": %d, \"userId\": %d, \"isAdmin\": %b }",
            fileId, userId, isAdmin
        );

        syncRepository.insertPendingChange(
            "files",
            fileId,
            "FILE_DELETE",
            payloadJson
        );
    }

    // ---------- RENAME ----------

    /**
     * Rename a file with online/offline handling.
     * NO LOGIC CHANGES: FileService.renameFile still works the same way
     */
    public File renameFile(long fileId, long userId, boolean isAdmin, String newName) {
        boolean online = connectivityService.isOnline();

        if (online) {
            try {
                File updated = fileService.renameFile(fileId, userId, isAdmin, newName);
                syncRepository.upsertCachedFile(
                    updated.getId(),
                    updated.getOwnerId(),
                    updated.getFilename(),
                    updated.getLastModified(),
                    "synced"
                );
                return updated;
            } catch (RuntimeException ex) {
                if (isSqlConnectionError(ex)) {
                    connectivityService.markOfflineOnSQLException((SQLException) ex.getCause());
                    return renameFileOffline(fileId, userId, isAdmin, newName);
                }
                throw ex;
            }
        } else {
            return renameFileOffline(fileId, userId, isAdmin, newName);
        }
    }

    /**
     * Offline rename: Update local cache and queue for later sync.
     * NO CHANGES: Offline logic remains the same
     */
    private File renameFileOffline(long fileId, long userId, boolean isAdmin, String newName) {
        // Update local cached_files row
        LocalDateTime now = LocalDateTime.now();
        syncRepository.updateCachedFileName(fileId, newName, now);

        String payloadJson = String.format(
            "{ \"fileId\": %d, \"userId\": %d, \"isAdmin\": %b, \"newName\": \"%s\" }",
            fileId, userId, isAdmin, newName
        );

        syncRepository.insertPendingChange(
            "files",
            fileId,
            "FILE_RENAME",
            payloadJson
        );

        // For the UI, return a synthetic File with updated name
        return new File(
            fileId,
            userId,          // ownerId (approx; you could read from cache)
            newName,
            0L,              // size unknown here unless you also cache it
            now,
            now
        );
    }

    // ---------- EDIT / DOWNLOAD ----------

    /**
     * Download a file with online/offline handling.
     * NO LOGIC CHANGES: FileService.downloadFile now uses MQTT/SFTP internally
     */
    public Path downloadFile(long fileId, long userId, boolean isAdmin) throws Exception {
        boolean online = connectivityService.isOnline();

        if (online) {
            try {
                // CHANGE 7: downloadFile now uses DownloadManager internally
                // But the signature and call remain the same
                return fileService.downloadFile(fileId, userId, isAdmin);
            } catch (Exception ex) {
                if (isSqlConnectionError(ex)) {
                    connectivityService.markOfflineOnSQLException((SQLException) ex.getCause());
                    // Fall through to offline path
                } else {
                    throw ex;
                }
            }
        }

        // Offline fallback:
        // Try the same method - will fail if chunks are missing
        // NOTE: In a real offline scenario, you'd need local chunk storage
        return fileService.downloadFile(fileId, userId, isAdmin);
    }

    // ---------- READ QUERIES ----------

    /**
     * Get user files with online/offline handling.
     * NO CHANGES: Read operations don't involve MQTT
     */
    public List<File> getUserFiles(long userId, boolean isAdmin) {
        if (connectivityService.isOnline()) {
            if (isAdmin) {
                return fileService.getAllFiles(true);
            } else {
                return fileService.getUserFiles(userId);
            }
        } else {
            return syncRepository.loadCachedFilesForUser(userId, isAdmin);
        }
    }

    // ---------- Cleanup ----------

    /**
     * CHANGE 8: Add shutdown method to cleanup MQTT connection
     * WHY: FileService now maintains MQTT connection that needs cleanup
     */
    public void shutdown() {
        if (fileService != null) {
            fileService.shutdown();
        }
    }

    // ---------- Helper ----------

    /**
     * NO CHANGES: Helper method remains the same
     */
    private boolean isSqlConnectionError(Throwable ex) {
        Throwable cause = ex.getCause();
        return cause instanceof SQLException;
    }
}
