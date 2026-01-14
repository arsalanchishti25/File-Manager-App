// src/main/java/com/example/mainapp/model/FileChunkMetadata.java
package com.example.mainapp.model;

/**
 * Represents metadata for a single file chunk stored in the database.
 * Does NOT contain actual chunk bytes - only metadata about where it's stored.
 */
public class FileChunkMetadata {

    private final long id;              // Primary key from filechunks table
    private final long fileId;          // Foreign key to files table
    private final int chunkOrder;       // 1, 2, 3, or 4
    private final String storageLocation; // FS container name (e.g., "fs-1")
    private final String crc32Checksum; // CRC32 checksum for integrity
    private final int volumeGroup;      // 1, 2, 3, or 4

    public FileChunkMetadata(long id, long fileId, int chunkOrder, String storageLocation, 
                             String crc32Checksum, int volumeGroup) {
        this.id = id;
        this.fileId = fileId;
        this.chunkOrder = chunkOrder;
        this.storageLocation = storageLocation;
        this.crc32Checksum = crc32Checksum;
        this.volumeGroup = volumeGroup;
    }

    // Getters
    public long getId() { return id; }
    public long getFileId() { return fileId; }
    public int getChunkOrder() { return chunkOrder; }
    public String getStorageLocation() { return storageLocation; }
    public String getCrc32Checksum() { return crc32Checksum; }
    public int getVolumeGroup() { return volumeGroup; }

    @Override
    public String toString() {
        return "Chunk[" + chunkOrder + "] -> " + storageLocation + " (VG" + volumeGroup + ")";
    }
}
