// src/main/java/com/example/mainapp/repository/FileRepository.java
package com.example.mainapp.repository;

import com.example.mainapp.model.File;
import com.example.mainapp.model.FileChunkMetadata;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for File and FileChunk metadata operations.
 * Main app only stores metadata in MySQL - actual chunking happens in Aggregator.
 */
public interface FileRepository {

    /**
     * Save a new file metadata record.
     * 
     * @param ownerId User ID who owns the file
     * @param filename Original filename
     * @param sizeInBytes File size in bytes
     * @return Saved File entity with generated ID
     */
    File saveFile(long ownerId, String filename, long sizeInBytes);

    /**
     * Find a file by its ID.
     * 
     * @param fileId File ID
     * @return Optional containing the file if found
     */
    Optional<File> findById(long fileId);

    /**
     * Find all files owned by a specific user.
     * 
     * @param ownerId User ID
     * @return List of files owned by the user
     */
    List<File> findByOwnerId(long ownerId);

    /**
     * Find all files in the system (admin view).
     * 
     * @return List of all files
     */
    List<File> findAll();

    /**
     * Update file metadata (e.g., last_modified, filename).
     * 
     * @param file File entity with updated values
     */
    void updateFile(File file);

    /**
     * Delete a file metadata record by ID.
     * Also deletes associated chunk metadata.
     * 
     * @param fileId File ID to delete
     */
    void deleteById(long fileId);

    /**
     * Check if a file exists by ID.
     * 
     * @param fileId File ID
     * @return true if exists, false otherwise
     */
    boolean existsById(long fileId);

    /**
     * Save chunk metadata after successful upload.
     * Called after Aggregator confirms chunks are stored in FS containers.
     * 
     * @param fileId File ID this chunk belongs to
     * @param chunkOrder Chunk order (1-4)
     * @param checksum CRC32 checksum of the encrypted chunk
     * @param storageLocation FS container name where chunk is stored (e.g., "fs-1")
     * @param volumeGroup Volume group (1-4)
     */
    void saveChunk(long fileId, int chunkOrder, String checksum, String storageLocation, int volumeGroup);

    /**
     * Get all chunk metadata for a specific file.
     * Used during download to know which FS containers hold the chunks.
     * 
     * @param fileId File ID
     * @return List of chunk metadata records
     */
    List<FileChunkMetadata> getChunksForFile(long fileId);

    /**
     * Delete all chunk metadata for a file.
     * Called during file deletion.
     * 
     * @param fileId File ID
     */
    void deleteChunksByFileId(long fileId);

    /**
     * Update file status (e.g., "UPLOADING" -> "READY").
     * 
     * @param fileId File ID
     * @param status New status
     */
    // void updateFileStatus(long fileId, String status);
}
