package com.example.mainapp.model;

import java.time.LocalDateTime;

public class File {

    private final long id;
    private final long ownerId;              // User ID of owner
    private final String filename;
    private final long sizeInBytes;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastModified;

    public File(long id, long ownerId, String filename, long sizeInBytes,
                LocalDateTime createdAt, LocalDateTime lastModified) {
        this.id = id;
        this.ownerId = ownerId;
        this.filename = filename;
        this.sizeInBytes = sizeInBytes;
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }

    public long getId() {
        return id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public String getFilename() {
        return filename;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public String getFormattedSize() {
        return formatBytes(sizeInBytes);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    public File withNewNameAndModified(String newName, LocalDateTime newModified) {
        return new File(this.id, this.ownerId, newName, this.sizeInBytes, this.createdAt, newModified);
    }
}
