package com.example.filestorage.service;

import com.example.filestorage.model.ContainerConfig;

/**
 * High-level storage service.
 */
public class StorageService {

    private final ChunkManager chunkManager;
    private final ContainerConfig config;

    public StorageService(ContainerConfig config) {
        this.config = config;
        this.chunkManager = new ChunkManager(config.getStorageBasePath());

        System.out.println("[StorageService] Initialized:");
        System.out.println("  Container ID: " + config.getContainerId());
        System.out.println("  Volume Group: " + config.getVolumeGroup());
        System.out.println("  Storage Path: " + config.getStorageBasePath());
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public ContainerConfig getConfig() {
        return config;
    }
}
