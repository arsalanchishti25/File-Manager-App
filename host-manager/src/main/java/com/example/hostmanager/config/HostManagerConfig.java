package com.example.hostmanager.config;

import java.util.Collections;
import java.util.Map;

/**
 * Centralized configuration for host-manager orchestrator.
 * Supports map injection for testability.
 */
public class HostManagerConfig {

    private final String hostManagerId;
    private final String mqttBrokerUrl;
    private final String fileStorageImage;
    private final String aggregatorImage;
    private final String dockerNetwork;
    private final String dockerHost;

    public HostManagerConfig(Map<String, String> env) {
        Map<String, String> source = env != null ? env : Collections.emptyMap();

        String id = source.get("HOST_MANAGER_ID");
        if (id == null || id.isBlank()) {
            id = source.get("SERVICE_ID");
        }
        this.hostManagerId = (id != null && !id.isBlank()) ? id.trim() : "hm-1";

        String broker = source.get("MQTT_BROKER_URL");
        this.mqttBrokerUrl = (broker != null && !broker.isBlank())
                ? broker.trim()
                : "tcp://filemanager-mqtt:1883";

        String fsImage = source.get("FILE_STORAGE_IMAGE");
        this.fileStorageImage = (fsImage != null && !fsImage.isBlank())
                ? fsImage.trim()
                : "file-manager/file-storage:latest";

        String aggImage = source.get("AGGREGATOR_IMAGE");
        this.aggregatorImage = (aggImage != null && !aggImage.isBlank())
                ? aggImage.trim()
                : "file-manager/aggregator:latest";

        String network = source.get("DOCKER_NETWORK");
        this.dockerNetwork = (network != null && !network.isBlank())
                ? network.trim()
                : "filemanager-network";

        String host = source.get("DOCKER_HOST");
        this.dockerHost = (host != null && !host.isBlank()) ? host.trim() : null;
    }

    public static HostManagerConfig fromEnv() {
        return new HostManagerConfig(System.getenv());
    }

    public static HostManagerConfig fromMap(Map<String, String> env) {
        return new HostManagerConfig(env);
    }

    public String getHostManagerId() {
        return hostManagerId;
    }

    public String getMqttBrokerUrl() {
        return mqttBrokerUrl;
    }

    public String getFileStorageImage() {
        return fileStorageImage;
    }

    public String getAggregatorImage() {
        return aggregatorImage;
    }

    public String getDockerNetwork() {
        return dockerNetwork;
    }

    public String getDockerHost() {
        return dockerHost;
    }
}
