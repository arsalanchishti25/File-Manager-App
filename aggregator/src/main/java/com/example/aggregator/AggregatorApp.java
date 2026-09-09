package com.example.aggregator;

import com.example.aggregator.model.AggregatorConfig;
import com.example.aggregator.mqtt.MqttBroker;
import com.example.aggregator.mqtt.MqttMessageHandler;
import com.example.aggregator.service.HealthReporter;
import com.example.aggregator.sftp.SftpServer;
import java.io.IOException;

/**
 * Aggregator Container Main Application.
 * Handles file chunking, encryption, and reassembly.
 */
public class AggregatorApp {

    private static SftpServer sftpServer;
    private static MqttBroker mqttBroker;
    private static MqttMessageHandler messageHandler;
    private static HealthReporter healthReporter;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║     AGGREGATOR CONTAINER STARTING      ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        try {
            // Read configuration from environment
            String aggregatorId = System.getenv("AGGREGATOR_ID");
            if (aggregatorId == null) {
                aggregatorId = "agg-1";  // Default for testing
                System.out.println("[AggregatorApp] Using default aggregator ID: " + aggregatorId);
            }

            int sftpPort = Integer.parseInt(System.getenv().getOrDefault("SFTP_PORT", "2222"));

            String workingDir = System.getenv("WORKING_DIR");
            if (workingDir == null) {
                workingDir = "/data/working";
            }

            String brokerUrl = System.getenv("MQTT_BROKER_URL");
            if (brokerUrl == null) {
                brokerUrl = "tcp://filemanager-mqtt:1883";  // Docker container hostname
            }

            // Create configuration
            AggregatorConfig config = new AggregatorConfig(
                    aggregatorId,
                    sftpPort,
                    workingDir,
                    brokerUrl
            );

            System.out.println("[AggregatorApp] Configuration:");
            System.out.println("  " + config);

            // Initialize SFTP Server
            sftpServer = new SftpServer(config.getAggregatorId(), config.getSftpPort(), 
                                       config.getWorkingDirectory());
            sftpServer.start();

            // Initialize MQTT Broker
            mqttBroker = new MqttBroker(config.getBrokerUrl(), config.getAggregatorId());
            mqttBroker.connect();

            // Initialize Message Handler (monitors SFTP directory for instructions)
            messageHandler = new MqttMessageHandler(mqttBroker, config);
            messageHandler.startMonitoring();

            // Start heartbeat reporting so Load Balancer knows this aggregator is alive
            healthReporter = new HealthReporter(config.getAggregatorId(), mqttBroker);
            healthReporter.start();

            System.out.println("\n╔════════════════════════════════════════╗");
            System.out.println("║      AGGREGATOR CONTAINER READY        ║");
            System.out.println("╚════════════════════════════════════════╝\n");
            System.out.println("[AggregatorApp] SFTP: " + aggregatorId + " on port " + sftpPort);
            System.out.println("[AggregatorApp] Working directory: " + workingDir);
            System.out.println("[AggregatorApp] Monitoring for incoming files...\n");

            // Graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[AggregatorApp] Shutting down gracefully...");
                if (healthReporter != null) {
                    healthReporter.stop();
                }
                if (messageHandler != null) {
                    messageHandler.stopMonitoring();
                }
                if (mqttBroker != null) {
                    mqttBroker.disconnect();
                }
                if (sftpServer != null) {
                    sftpServer.stop();
                }
                System.out.println("[AggregatorApp] Shutdown complete.");
            }));

            // Keep application running
            Thread.currentThread().join();

        } catch (IOException e) {
            System.err.println("[AggregatorApp] SFTP Server Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[AggregatorApp] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
