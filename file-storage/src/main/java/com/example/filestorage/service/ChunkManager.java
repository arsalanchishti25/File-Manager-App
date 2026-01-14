package com.example.filestorage.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages chunk storage and retrieval.
 */
public class ChunkManager {

    private final String storageBasePath;

    public ChunkManager(String storageBasePath) {
        this.storageBasePath = storageBasePath;
    }

    /**
     * Store a chunk file.
     * File path: {storageBasePath}/fileId_{fileId}_chunk{chunkOrder}.enc
     */
    public void storeChunk(long fileId, int chunkOrder, byte[] chunkData) throws IOException {
        String filename = String.format("fileId_%d_chunk%d.enc", fileId, chunkOrder);
        Path filePath = Paths.get(storageBasePath, filename);

        // TODO: Validate chunk data integrity (CRC32, size, etc.)
        
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(chunkData);
            fos.flush();
            System.out.println("[ChunkManager] Stored chunk: " + filename + 
                             " (size: " + chunkData.length + " bytes)");
        }
    }

    /**
     * Retrieve a chunk file.
     */
    public byte[] retrieveChunk(long fileId, int chunkOrder) throws IOException {
        String filename = String.format("fileId_%d_chunk%d.enc", fileId, chunkOrder);
        Path filePath = Paths.get(storageBasePath, filename);

        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("Chunk not found: " + filename);
        }

        // TODO: Validate chunk integrity before returning
        
        byte[] chunkData = Files.readAllBytes(filePath);
        System.out.println("[ChunkManager] Retrieved chunk: " + filename + 
                         " (size: " + chunkData.length + " bytes)");
        return chunkData;
    }

    /**
     * Delete a chunk file.
     */
    public void deleteChunk(long fileId, int chunkOrder) throws IOException {
        String filename = String.format("fileId_%d_chunk%d.enc", fileId, chunkOrder);
        Path filePath = Paths.get(storageBasePath, filename);

        if (Files.exists(filePath)) {
            Files.delete(filePath);
            System.out.println("[ChunkManager] Deleted chunk: " + filename);
        } else {
            System.out.println("[ChunkManager] Chunk not found (not_found): " + filename);
        }
    }

    /**
     * Check if a chunk exists.
     */
    public boolean chunkExists(long fileId, int chunkOrder) {
        String filename = String.format("fileId_%d_chunk%d.enc", fileId, chunkOrder);
        Path filePath = Paths.get(storageBasePath, filename);
        return Files.exists(filePath);
    }

    /**
     * Delete all chunks for a file.
     */
    public void deleteFileChunks(long fileId) throws IOException {
        System.out.println("[ChunkManager] Deleting all chunks for fileId: " + fileId);
        
        for (int i = 1; i <= 4; i++) {
            try {
                deleteChunk(fileId, i);
            } catch (IOException e) {
                System.out.println("[ChunkManager] Chunk " + i + " not found or already deleted");
            }
        }
    }

    /**
     * Get storage usage statistics.
     */
    public Map<String, Long> getStorageStats() throws IOException {
        Map<String, Long> stats = new HashMap<>();
        long totalSize = 0;
        int fileCount = 0;
        int chunkCount = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(storageBasePath))) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    totalSize += Files.size(path);
                    chunkCount++;
                    fileCount++;
                }
            }
        }

        stats.put("totalBytes", totalSize);
        stats.put("fileCount", (long) fileCount);
        stats.put("chunkCount", (long) chunkCount);

        return stats;
    }
}
