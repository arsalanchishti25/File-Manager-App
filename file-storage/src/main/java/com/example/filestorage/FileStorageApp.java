package com.example.filestorage;

import com.example.filestorage.config.FileStorageAppConfig;
import com.example.filestorage.model.ContainerConfig;
import com.example.filestorage.mqtt.MqttBroker;
import com.example.filestorage.mqtt.MqttMessageHandler;
import com.example.filestorage.service.HealthReporter;
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
    private static HealthReporter healthReporter;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║     FILE STORAGE CONTAINER STARTING    ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        try {
            FileStorageAppConfig appConfig = FileStorageAppConfig.fromEnv();
            ContainerConfig config = appConfig.toModel();

            System.out.println("[FileStorageApp] Configuration:");
            System.out.println("  " + config);

            // Initialize SFTP Server
            sftpServer = new SftpServer(config.getContainerId(), config.getSftpPort(),
                    config.getStorageBasePath(), appConfig.requireSftpUser(), appConfig.requireSftpPassword());
            sftpServer.start();

            // Initialize Storage Service
            StorageService storageService = new StorageService(config);

            // Initialize MQTT Broker
            mqttBroker = new MqttBroker(config.getBrokerUrl(), config.getContainerId());
            mqttBroker.connect();

            // Initialize Message Handler
            messageHandler = new MqttMessageHandler(mqttBroker, storageService);
            messageHandler.startListening();

            // Start heartbeat reporting so Load Balancer knows this container is alive
            healthReporter = new HealthReporter(config.getContainerId(), mqttBroker);
            healthReporter.start();

            System.out.println("\n╔════════════════════════════════════════╗");
            System.out.println("║   FILE STORAGE CONTAINER READY         ║");
            System.out.println("╚════════════════════════════════════════╝\n");
            System.out.println("[FileStorageApp] SFTP: " + config.getContainerId() + " on port " + config.getSftpPort());
            System.out.println("[FileStorageApp] Storage: " + config.getStorageBasePath());
            System.out.println("[FileStorageApp] Listening for MQTT delete commands...\n");

            // Graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[FileStorageApp] Shutting down gracefully...");
                if (healthReporter != null) {
                    healthReporter.stop();
                }
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
