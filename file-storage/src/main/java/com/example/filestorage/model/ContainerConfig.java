package com.example.filestorage.model;

/**
 * Configuration for File Storage Container.
 */
public class ContainerConfig {
    private final String containerId;
    private final int volumeGroup;
    private final int sftpPort;
    private final String storageBasePath;
    private final String brokerUrl;

    public ContainerConfig(String containerId, int volumeGroup, int sftpPort, 
                          String storageBasePath, String brokerUrl) {
        this.containerId = containerId;
        this.volumeGroup = volumeGroup;
        this.sftpPort = sftpPort;
        this.storageBasePath = storageBasePath;
        this.brokerUrl = brokerUrl;
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

    public String getStorageBasePath() {
        return storageBasePath;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    @Override
    public String toString() {
        return "ContainerConfig{" +
                "containerId='" + containerId + '\'' +
                ", volumeGroup=" + volumeGroup +
                ", sftpPort=" + sftpPort +
                ", storageBasePath='" + storageBasePath + '\'' +
                ", brokerUrl='" + brokerUrl + '\'' +
                '}';
    }
}
