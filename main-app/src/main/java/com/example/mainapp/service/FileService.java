package com.example.mainapp.service;

import com.example.mainapp.model.File;
import com.example.mainapp.repository.FileRepository;
import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.repository.MySQLFileRepository;
import com.example.mainapp.mqtt.MqttClient;
import com.example.mainapp.mqtt.UploadManager;
import com.example.mainapp.mqtt.DownloadManager;
import com.example.mainapp.mqtt.DeleteManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * File service for the Main App.
 * Handles file metadata operations and coordinates with Load Balancer for distributed operations.
 * 
 * ARCHITECTURE:
 * - Main app NO LONGER does chunking/encryption (that's in Aggregator)
 * - Main app NO LONGER stores chunks locally (that's in FS containers)
 * - Main app communicates via MQTT with Load Balancer
 * - Main app sends/receives actual files via SFTP to/from Aggregator
 * 
 * CHANGE: Now delegates upload/download/delete to specialized Manager classes
 */
public class FileService {

    private final FileRepository fileRepository;
    private final PermissionService permissionService;
    private final MqttClient mqttClient;
    // private final String mainAppId;
    
    // CHANGE 1: Add manager instances for distributed operations
    private UploadManager uploadManager;
    private DownloadManager downloadManager;
    private DeleteManager deleteManager;
    
    // Temporary storage for files before/after SFTP transfer
    private final Path tempUploadDir = Paths.get("data/temp/uploads");
    private final Path tempDownloadDir = Paths.get("data/temp/downloads");

    /**
     * CHANGE 2: Updated default constructor
     * NOW: Takes MQTT client and main app ID as parameters
     * WHY: Managers need MQTT client to communicate with LB
     */
    public FileService(String brokerUrl, String mainAppId) throws Exception {
        this.fileRepository = new MySQLFileRepository(new RemoteMySQLDataSource());
        this.permissionService = new PermissionService();
        // this.mainAppId = mainAppId;
        
        // CHANGE 3: Initialize MQTT client
        this.mqttClient = MqttClient.shared(brokerUrl, mainAppId);
        this.mqttClient.connect();
        
        // CHANGE 4: Initialize managers with dependencies
        this.uploadManager = new UploadManager(mqttClient, fileRepository, mainAppId);
        this.downloadManager = new DownloadManager(mqttClient, fileRepository, mainAppId);
        this.deleteManager = new DeleteManager(mqttClient, fileRepository, permissionService, mainAppId);
        
        initializeTempDirectories();
    }
    
    /**
     * CHANGE 5: Updated DI-friendly constructor for testing
     * NOW: Accepts MqttClient as parameter instead of creating one
     * WHY: Makes testing easier - can inject mock MQTT client
     */
    public FileService(FileRepository fileRepository, PermissionService permissionService, 
                       MqttClient mqttClient, String mainAppId) {
        this.fileRepository = fileRepository;
        this.permissionService = permissionService;
        this.mqttClient = mqttClient;
        // this.mainAppId = mainAppId;
        
        // Initialize managers with injected dependencies
        this.uploadManager = new UploadManager(mqttClient, fileRepository, mainAppId);
        this.downloadManager = new DownloadManager(mqttClient, fileRepository, mainAppId);
        this.deleteManager = new DeleteManager(mqttClient, fileRepository, permissionService, mainAppId);
        
        initializeTempDirectories();
    }

    /**
     * Initialize temporary directories for file transfers.
     */
    private void initializeTempDirectories() {
        try {
            Files.createDirectories(tempUploadDir);
            Files.createDirectories(tempDownloadDir);
        } catch (IOException e) {
            System.err.println("Failed to create temp directories: " + e.getMessage());
        }
    }

    /**
     * Upload a file to the distributed storage system.
     * 
     * 
     * NEW: Delegates to UploadManager.uploadFile()
     * 
     * WHY: Separation of concerns
     * - FileService: High-level file operations
     * - UploadManager: Detailed MQTT/SFTP upload orchestration
     * 
     * @param ownerId User ID who owns the file
     * @param sourcePath Path to the file on user's system
     * @return File metadata entity
     * @throws Exception if upload fails
     */

    public File uploadFile(long ownerId, Path sourcePath) throws Exception {
        // CHANGE 7: Call uploadManager instead of inline implementation
        return uploadManager.uploadFile(ownerId, sourcePath, Files.size(sourcePath));
    }

    /**
     * Download a file from the distributed storage system.
     * 
     * 
     * NEW: Delegates to DownloadManager.downloadFile()
     * 
     * WHY: Separation of concerns
     * - FileService: High-level file operations
     * - DownloadManager: Detailed MQTT/SFTP download orchestration
     * 
     * @param fileId File ID to download
     * @param userId User requesting the download
     * @param isAdmin Whether the user is an admin
     * @return Path to the downloaded file in temp directory
     * @throws Exception if download fails
     */

    public Path downloadFile(long fileId, long userId, boolean isAdmin) throws Exception {
        // CHANGE 9: Call downloadManager instead of inline implementation
        return downloadManager.downloadFile(fileId, userId, isAdmin);
    }

    public File updateFile(File existingFile, long userId, Path sourcePath) throws Exception {
        if (existingFile == null || existingFile.getOwnerId() != userId) {
            throw new IllegalArgumentException("Permission denied");
        }
        var lock = FileOperationLock.forFile(existingFile.getId());
        lock.lockInterruptibly();
        try {
            File current = fileRepository.findById(existingFile.getId())
                    .orElseThrow(() -> new IllegalStateException("File was deleted while editing"));
            System.out.println("[FileService] Update identity check: snapshot fileId="
                    + existingFile.getId() + ", filename=" + existingFile.getFilename()
                    + ", ownerId=" + existingFile.getOwnerId()
                    + ", capturedLastModified=" + existingFile.getLastModified()
                    + ", currentLastModified=" + current.getLastModified()
                    + ", currentFilename=" + current.getFilename()
                    + ", currentOwnerId=" + current.getOwnerId());
            validateUpdateIdentity(existingFile, current, userId);
            return uploadManager.updateFile(current, userId, sourcePath, Files.size(sourcePath),
                    existingFile.getFilename());
        } finally {
            lock.unlock();
        }
    }

    public File captureUpdateSnapshot(File selectedFile, long userId) {
        if (selectedFile == null || selectedFile.getId() <= 0
                || selectedFile.getOwnerId() != userId
                || selectedFile.getFilename() == null
                || selectedFile.getFilename().isBlank()) {
            throw new IllegalArgumentException("Invalid file identity");
        }
        File remote = fileRepository.findById(selectedFile.getId())
                .orElseThrow(() -> new IllegalStateException("File was deleted while editing"));
        if (remote.getOwnerId() != userId
                || !remote.getFilename().equals(selectedFile.getFilename())) {
            throw new IllegalStateException("The file identity changed while editing");
        }
        return remote;
    }

    private static LocalDateTime normalizeVersion(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    static void validateUpdateIdentity(File snapshot, File current, long userId) {
        if (snapshot == null || current == null
                || snapshot.getId() <= 0
                || snapshot.getId() != current.getId()
                || snapshot.getOwnerId() != userId
                || current.getOwnerId() != userId
                || !Objects.equals(snapshot.getFilename(), current.getFilename())) {
            throw new IllegalArgumentException("File update identity does not match existing metadata");
        }
        if (!Objects.equals(normalizeVersion(current.getLastModified()),
                normalizeVersion(snapshot.getLastModified()))) {
            throw new IllegalStateException(
                    "The file changed elsewhere. Reopen it before saving.");
        }
    }

    /**
     * Helper to get the stored path (legacy, not used in distributed mode).
     */
    @Deprecated
    public Path getStoredPath(File file) {
        return Paths.get("storage").resolve(file.getFilename());
    }

    /**
     * Open file for editing (future implementation).
     */
    public void openFileForEditing(File file) throws IOException {
        // TODO: Download file first, then open
        // java.awt.Desktop.getDesktop().edit(downloadedPath.toFile());
    }

    /**
     * Get all files for a specific user (owned + shared).
     * NO CHANGES - This method doesn't involve distributed operations
     */
    public List<File> getUserFiles(long userId) {
        // 1) Own files
        List<File> ownFiles = fileRepository.findByOwnerId(userId);

        // 2) Files shared with this user
        List<Long> sharedFileIds = permissionService.findFileIdsSharedWithUser(userId);

        List<File> sharedFiles = new ArrayList<>();
        if (!sharedFileIds.isEmpty()) {
            for (Long fid : sharedFileIds) {
                fileRepository.findById(fid).ifPresent(sharedFiles::add);
            }
        }

        // 3) Merge, avoiding duplicates
        Map<Long, File> unique = new LinkedHashMap<>();
        for (File f : ownFiles) unique.put(f.getId(), f);
        for (File f : sharedFiles) unique.put(f.getId(), f);

        return new ArrayList<>(unique.values());
    }

    /**
     * Get a specific file (check ownership).
     * NO CHANGES - This method doesn't involve distributed operations
     */
    public Optional<File> getFile(long fileId, long userId) {
        Optional<File> fileOpt = fileRepository.findById(fileId);
        
        if (fileOpt.isPresent() && fileOpt.get().getOwnerId() != userId) {
            return Optional.empty(); // Access denied
        }

        return fileOpt;
    }

    /**
     * Delete a file from the distributed storage system.
     * 
     * 
     * NEW: Delegates to DeleteManager.deleteFile()
     * 
     * WHY: Separation of concerns
     * - FileService: High-level file operations
     * - DeleteManager: Detailed MQTT deletion orchestration
     * 
     * @param fileId File ID to delete
     * @param userId User requesting deletion
     * @param isAdmin Whether user is admin
     * @throws Exception if deletion fails
     */

    public void deleteFile(long fileId, long userId, boolean isAdmin) throws Exception {
        // CHANGE 11: Call deleteManager instead of inline implementation
        deleteManager.deleteFile(fileId, userId, isAdmin);
    }

    /**
     * Get all files (admin only).
     * NO CHANGES - This method doesn't involve distributed operations
     */
    public List<File> getAllFiles(boolean isAdmin) {
        if (!isAdmin) {
            throw new IllegalArgumentException("Only admins can view all files.");
        }
        return fileRepository.findAll();
    }

    /**
     * Rename a file (metadata only).
     * NO CHANGES - This method doesn't involve distributed operations
     */
    public File renameFile(long fileId, long userId, boolean isAdmin, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("New filename cannot be empty.");
        }

        var fileOpt = fileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("File not found.");
        }

        var file = fileOpt.get();
        if (!isAdmin && file.getOwnerId() != userId) {
            throw new IllegalArgumentException("You do not have permission to rename this file.");
        }

        var updated = file.withNewNameAndModified(newName, java.time.LocalDateTime.now());
        fileRepository.updateFile(updated);
        return updated;
    }

    // CHANGE 12: Add getter for MQTT client (for testing or if needed elsewhere)
    public MqttClient getMqttClient() {
        return mqttClient;
    }

    // CHANGE 13: Add cleanup method to disconnect MQTT on shutdown
    public void shutdown() {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
    }
}
