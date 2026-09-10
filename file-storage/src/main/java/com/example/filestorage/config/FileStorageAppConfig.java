package com.example.filestorage.config;

import com.example.filestorage.model.ContainerConfig;

import java.util.Collections;
import java.util.Map;

/**
 * Centralized configuration for file-storage service.
 * Supports map injection for testability and avoids hardcoded credential fallbacks.
 */
public class FileStorageAppConfig {

    private final String containerId;
    private final int volumeGroup;
    private final int sftpPort;
    private final String storageDataDir;
    private final String brokerUrl;
    private final String sftpUser;
    private final String sftpPassword;

    public FileStorageAppConfig(Map<String, String> env) {
        Map<String, String> source = env != null ? env : Collections.emptyMap();

        String id = source.get("FS_CONTAINER_ID");
        if (id == null || id.isBlank()) {
            id = source.get("SERVICE_ID");
        }
        this.containerId = (id != null && !id.isBlank()) ? id.trim() : "fs-1";

        // Volume Group: explicit env takes precedence; otherwise deduce from containerId
        String vgStr = source.get("VOLUME_GROUP");
        int parsedVg = 1;
        if (vgStr != null && !vgStr.isBlank()) {
            try {
                parsedVg = Integer.parseInt(vgStr.trim());
            } catch (NumberFormatException e) {
                parsedVg = parseVolumeGroupFromId(this.containerId);
            }
        } else {
            parsedVg = parseVolumeGroupFromId(this.containerId);
        }
        this.volumeGroup = parsedVg;

        String portStr = source.get("SFTP_PORT");
        int parsedPort = 2222;
        if (portStr != null && !portStr.isBlank()) {
            try {
                parsedPort = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                parsedPort = 2222;
            }
        }
        this.sftpPort = parsedPort;

        String dataDir = source.get("STORAGE_DATA_DIR");
        if (dataDir == null || dataDir.isBlank()) {
            dataDir = source.get("STORAGE_BASE_PATH");
        }
        this.storageDataDir = (dataDir != null && !dataDir.isBlank())
                ? dataDir.trim()
                : "/data/volume-group-" + this.volumeGroup + "/chunks";

        String broker = source.get("MQTT_BROKER_URL");
        this.brokerUrl = (broker != null && !broker.isBlank())
                ? broker.trim()
                : "tcp://filemanager-mqtt:1883";

        this.sftpUser = source.get("SFTP_USER");
        this.sftpPassword = source.get("SFTP_PASSWORD");
    }

    public static FileStorageAppConfig fromEnv() {
        return new FileStorageAppConfig(System.getenv());
    }

    public static FileStorageAppConfig fromMap(Map<String, String> env) {
        return new FileStorageAppConfig(env);
    }

    public static int parseVolumeGroupFromId(String containerId) {
        if (containerId == null) {
            return 1;
        }
        String cleaned = containerId.replaceAll("(?i)[^0-9]", "");
        if (cleaned.isEmpty()) {
            return 1;
        }
        try {
            int num = Integer.parseInt(cleaned);
            return ((num - 1) % 4) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public String getContainerId() {
        return containerId;
    }

    public int getVolumeGroup() {
        return volumeGroup;
    }

    public int getSftpPort() {
        return sftpPort;
    }

    public String getStorageDataDir() {
        return storageDataDir;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public String getSftpUser() {
        return sftpUser;
    }

    public String requireSftpUser() {
        if (sftpUser == null || sftpUser.isBlank()) {
            throw new IllegalStateException("Missing required configuration: SFTP_USER");
        }
        return sftpUser;
    }

    public String getSftpPassword() {
        return sftpPassword;
    }

    public String requireSftpPassword() {
        if (sftpPassword == null || sftpPassword.isBlank()) {
            throw new IllegalStateException("Missing required configuration: SFTP_PASSWORD");
        }
        return sftpPassword;
    }

    public ContainerConfig toModel() {
        return new ContainerConfig(containerId, volumeGroup, sftpPort, storageDataDir, brokerUrl);
    }
}
