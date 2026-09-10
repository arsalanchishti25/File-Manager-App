// src/main/java/com/example/hostmanager/HostManagerMain.java
package com.example.hostmanager;

import com.example.hostmanager.config.HostManagerConfig;
import com.example.hostmanager.service.ContainerOrchestrator;
import com.example.hostmanager.service.HostManager;

// import java.util.List;

/**
 * Main entry point for Host Manager application.
 * Listens for scaling commands from Load Balancer via MQTT and orchestrates container operations.
 */
public class HostManagerMain {

    private static HostManager hostManager;
    private static ContainerOrchestrator orchestrator;
    private static String hostManagerId;
    private static String mqttBrokerUrl;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("     Host Manager Starting             ");
        System.out.println("========================================");

        // Load configuration
        loadConfiguration(args);

        // Initialize services
        initializeServices();

        // Test Docker availability
        if (!hostManager.testDockerAvailability()) {
            System.err.println("[" + hostManagerId + "] FATAL: Docker is not available. Exiting.");
            System.exit(1);
        }

        // TODO: Initialize MQTT client to receive scaling commands from Load Balancer
        // initializeMqttClient();

        System.out.println("[" + hostManagerId + "] Host Manager ready. Waiting for scaling commands...");

        // Keep application running
        keepAlive();
    }

    /**
     * Load configuration from environment variables or command-line arguments.
     */
    private static void loadConfiguration(String[] args) {
        HostManagerConfig config = HostManagerConfig.fromEnv();
        hostManagerId = config.getHostManagerId();
        mqttBrokerUrl = config.getMqttBrokerUrl();

        System.out.println("Configuration loaded:");
        System.out.println("  Host Manager ID:  " + hostManagerId);
        System.out.println("  MQTT Broker:      " + mqttBrokerUrl);
    }

    /**
     * Initialize HostManager and ContainerOrchestrator.
     */
    private static void initializeServices() {
        try {
            hostManager = new HostManager(hostManagerId);
            orchestrator = new ContainerOrchestrator(hostManagerId, hostManager);
            System.out.println("[" + hostManagerId + "] Services initialized successfully.");
        } catch (Exception e) {
            System.err.println("[" + hostManagerId + "] FATAL: Failed to initialize services");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * TODO: Initialize MQTT client to receive scaling commands from Load Balancer.
     * 
     * Expected subscriptions:
     * 
     * 1. Topic: "hostmanager/scale/fs/up"
     *    Payload: {} (empty or minimal)
     *    Action: Call orchestrator.scaleUpFileStorage()
     *    Response: Publish to "hostmanager/scale/fs/up/response" with list of new container names
     * 
     * 2. Topic: "hostmanager/scale/fs/down"
     *    Payload: {} (empty or minimal)
     *    Action: Call orchestrator.scaleDownFileStorage()
     *    Response: Publish to "hostmanager/scale/fs/down/response" with list of removed containers
     * 
     * 3. Topic: "hostmanager/scale/aggregator/up"
     *    Payload: {} (empty or minimal)
     *    Action: Call orchestrator.scaleUpAggregator()
     *    Response: Publish to "hostmanager/scale/aggregator/up/response" with new container name
     * 
     * 4. Topic: "hostmanager/scale/aggregator/down"
     *    Payload: {} (empty or minimal)
     *    Action: Call orchestrator.scaleDownAggregator()
     *    Response: Publish to "hostmanager/scale/aggregator/down/response" with removed container name
     * 
     * 5. Topic: "hostmanager/status"
     *    Payload: {} (query)
     *    Action: Return current container counts
     *    Response: Publish to "hostmanager/status/response" with JSON:
     *              {
     *                "fsContainers": 8,
     *                "aggContainers": 2,
     *                "runningContainers": [...]
     *              }
     */
    // private static void initializeMqttClient() {
    //     System.out.println("[" + hostManagerId + "] TODO: Initialize MQTT client connecting to " + mqttBrokerUrl);
    //     // Pseudo-code:
    //     // MqttClient client = new MqttClient(mqttBrokerUrl, hostManagerId);
    //     // 
    //     // // Subscribe to scale UP commands
    //     // client.subscribe("hostmanager/scale/fs/up", (topic, payload) -> {
    //     //     System.out.println("[" + hostManagerId + "] Received SCALE UP FS command");
    //     //     List<String> newContainers = orchestrator.scaleUpFileStorage();
    //     //     
    //     //     String response = "{\"success\": true, \"containers\": [" 
    //     //                       + String.join(",", newContainers.stream()
    //     //                           .map(c -> "\"" + c + "\"")
    //     //                           .toArray(String[]::new)) 
    //     //                       + "]}";
    //     //     client.publish("hostmanager/scale/fs/up/response", response);
    //     // });
    //     // 
    //     // // Subscribe to scale DOWN commands
    //     // client.subscribe("hostmanager/scale/fs/down", (topic, payload) -> {
    //     //     System.out.println("[" + hostManagerId + "] Received SCALE DOWN FS command");
    //     //     List<String> removed = orchestrator.scaleDownFileStorage();
    //     //     
    //     //     String response = "{\"success\": true, \"removed\": [" 
    //     //                       + String.join(",", removed.stream()
    //     //                           .map(c -> "\"" + c + "\"")
    //     //                           .toArray(String[]::new)) 
    //     //                       + "]}";
    //     //     client.publish("hostmanager/scale/fs/down/response", response);
    //     // });
    //     // 
    //     // // Subscribe to Aggregator scale commands
    //     // client.subscribe("hostmanager/scale/aggregator/up", (topic, payload) -> {
    //     //     System.out.println("[" + hostManagerId + "] Received SCALE UP Aggregator command");
    //     //     String newContainer = orchestrator.scaleUpAggregator();
    //     //     
    //     //     String response = "{\"success\": " + (newContainer != null) 
    //     //                       + ", \"container\": \"" + newContainer + "\"}";
    //     //     client.publish("hostmanager/scale/aggregator/up/response", response);
    //     // });
    //     // 
    //     // client.subscribe("hostmanager/scale/aggregator/down", (topic, payload) -> {
    //     //     System.out.println("[" + hostManagerId + "] Received SCALE DOWN Aggregator command");
    //     //     String removed = orchestrator.scaleDownAggregator();
    //     //     
    //     //     String response = "{\"success\": " + (removed != null) 
    //     //                       + ", \"removed\": \"" + removed + "\"}";
    //     //     client.publish("hostmanager/scale/aggregator/down/response", response);
    //     // });
    //     // 
    //     // // Subscribe to status queries
    //     // client.subscribe("hostmanager/status", (topic, payload) -> {
    //     //     List<ContainerOrchestrator.ContainerInfo> containers = orchestrator.getRunningContainers();
    //     //     int fsCount = orchestrator.getContainerCount("FILE_STORAGE");
    //     //     int aggCount = orchestrator.getContainerCount("AGGREGATOR");
    //     //     
    //     //     String response = "{\"fsContainers\": " + fsCount 
    //     //                       + ", \"aggContainers\": " + aggCount 
    //     //                       + ", \"total\": " + containers.size() + "}";
    //     //     client.publish("hostmanager/status/response", response);
    //     // });
    //     // 
    //     // client.connect();
    // }

    /**
     * Keep the application running.
     */
    private static void keepAlive() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[" + hostManagerId + "] Shutting down Host Manager...");
            
            // TODO: Cleanup
            // - Disconnect MQTT
            // - Optionally stop all managed containers
        }
    }

    // Expose services for testing
    public static ContainerOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public static HostManager getHostManager() {
        return hostManager;
    }
}
