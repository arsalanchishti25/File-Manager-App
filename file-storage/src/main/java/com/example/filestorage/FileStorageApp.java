package com.example.filestorage;

import com.example.filestorage.model.ContainerConfig;
import com.example.filestorage.mqtt.MqttBroker;
import com.example.filestorage.mqtt.MqttMessageHandler;
import com.example.filestorage.service.StorageService;
import com.example.filestorage.sftp.SftpServer;
import org.eclipse.paho.client.mqttv3.MqttException;
import java.io.IOException;

/**
 * File Storage Container Main Application.
 * Provides SFTP storage and handles delete commands via MQTT.
 */
public class FileStorageApp {

    private static SftpServer sftpServer;
    private static MqttBroker mqttBroker;
    private static MqttMessageHandler messageHandler;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║     FILE STORAGE CONTAINER STARTING    ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        try {
            // Read configuration from environment
            String containerId = System.getenv("FS_CONTAINER_ID");
            if (containerId == null) {
                containerId = "fs-1";  // Default for testing
                System.out.println("[FileStorageApp] Using default container ID: " + containerId);
            }

            // Extract volume group from container ID
            // FS-1 → VG 1, FS-2 → VG 2, FS-3 → VG 3, FS-4 → VG 4, FS-5 → VG 1, etc.
            int fsNumber = Integer.parseInt(containerId.replace("fs-", "").replace("FS-", ""));
            int volumeGroup = ((fsNumber - 1) % 4) + 1;

            int sftpPort = Integer.parseInt(System.getenv().getOrDefault("SFTP_PORT", "2222"));
            
            // TODO: Update storage path based on volume group
            String storageBasePath = System.getenv("STORAGE_BASE_PATH");
            if (storageBasePath == null) {
                storageBasePath = "/data/volume-group-" + volumeGroup + "/chunks";
            }

            String brokerUrl = System.getenv("MQTT_BROKER_URL");
            if (brokerUrl == null) {
                brokerUrl = "tcp://filemanager-mqtt:1883";  // Docker container hostname
            }

            // Create configuration
            ContainerConfig config = new ContainerConfig(
                    containerId.toLowerCase(),
                    volumeGroup,
                    sftpPort,
                    storageBasePath,
                    brokerUrl
            );

            System.out.println("[FileStorageApp] Configuration:");
            System.out.println("  " + config);

            // Initialize SFTP Server
            sftpServer = new SftpServer(config.getContainerId(), config.getSftpPort(), 
                                       config.getStorageBasePath());
            sftpServer.start();

            // Initialize Storage Service
            StorageService storageService = new StorageService(config);

            // Initialize MQTT Broker
            mqttBroker = new MqttBroker(config.getBrokerUrl(), config.getContainerId());
            mqttBroker.connect();

            // Initialize Message Handler
            messageHandler = new MqttMessageHandler(mqttBroker, storageService);
            messageHandler.startListening();

            System.out.println("\n╔════════════════════════════════════════╗");
            System.out.println("║   FILE STORAGE CONTAINER READY         ║");
            System.out.println("╚════════════════════════════════════════╝\n");
            System.out.println("[FileStorageApp] SFTP: " + containerId + " on port " + sftpPort);
            System.out.println("[FileStorageApp] Storage: " + storageBasePath);
            System.out.println("[FileStorageApp] Listening for MQTT delete commands...\n");

            // Graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[FileStorageApp] Shutting down gracefully...");
                if (mqttBroker != null) {
                    mqttBroker.disconnect();
                }
                if (sftpServer != null) {
                    sftpServer.stop();
                }
                System.out.println("[FileStorageApp] Shutdown complete.");
            }));

            // Keep application running
            Thread.currentThread().join();

        } catch (MqttException e) {
            System.err.println("[FileStorageApp] MQTT Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("[FileStorageApp] SFTP Server Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            System.err.println("[FileStorageApp] Interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[FileStorageApp] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
