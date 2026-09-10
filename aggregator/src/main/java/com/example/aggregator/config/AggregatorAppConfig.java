package com.example.aggregator.config;

import com.example.aggregator.model.AggregatorConfig;

import java.util.Collections;
import java.util.Map;

/**
 * Centralized configuration for aggregator service.
 * Supports map injection for testability and avoids hardcoded credential fallbacks.
 */
public class AggregatorAppConfig {

    private final String aggregatorId;
    private final int sftpPort;
    private final String workingDirectory;
    private final String brokerUrl;
    private final String sftpUser;
    private final String sftpPassword;
    private final String encryptionKey;

    public AggregatorAppConfig(Map<String, String> env) {
        Map<String, String> source = env != null ? env : Collections.emptyMap();

        String id = source.get("AGGREGATOR_ID");
        if (id == null || id.isBlank()) {
            id = source.get("SERVICE_ID");
        }
        this.aggregatorId = (id != null && !id.isBlank()) ? id.trim() : "agg-1";

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

        String workDir = source.get("AGGREGATOR_WORK_DIR");
        if (workDir == null || workDir.isBlank()) {
            workDir = source.get("WORKING_DIR");
        }
        this.workingDirectory = (workDir != null && !workDir.isBlank())
                ? workDir.trim()
                : "/data/working";

        String broker = source.get("MQTT_BROKER_URL");
        this.brokerUrl = (broker != null && !broker.isBlank())
                ? broker.trim()
                : "tcp://filemanager-mqtt:1883";

        this.sftpUser = source.get("SFTP_USER");
        this.sftpPassword = source.get("SFTP_PASSWORD");
        this.encryptionKey = source.get("ENCRYPTION_KEY");
    }

    public static AggregatorAppConfig fromEnv() {
        return new AggregatorAppConfig(System.getenv());
    }

    public static AggregatorAppConfig fromMap(Map<String, String> env) {
        return new AggregatorAppConfig(env);
    }

    public String getAggregatorId() {
        return aggregatorId;
    }

    public int getSftpPort() {
        return sftpPort;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
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

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public String requireEncryptionKey() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("Missing required configuration: ENCRYPTION_KEY");
        }
        return encryptionKey;
    }

    public AggregatorConfig toModel() {
        return new AggregatorConfig(aggregatorId, sftpPort, workingDirectory, brokerUrl);
    }
}
