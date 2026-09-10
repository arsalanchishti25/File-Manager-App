package com.example.loadbalancer;

import com.example.loadbalancer.config.LoadBalancerConfig;
import com.example.loadbalancer.mqtt.MqttBroker;
import com.example.loadbalancer.mqtt.MqttMessageHandler;
import com.example.loadbalancer.mqtt.TopicConstants;
import com.example.loadbalancer.service.HealthMonitor;
import com.example.loadbalancer.service.RoutingService;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Load Balancer Main Application.
 * Orchestrates file operations via MQTT.
 */
public class LoadBalancerApp {

    private static MqttBroker mqttBroker;
    private static MqttMessageHandler messageHandler;
    private static HealthMonitor healthMonitor;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║        LOAD BALANCER STARTING          ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        try {
            LoadBalancerConfig config = LoadBalancerConfig.fromEnv();
            String brokerUrl = config.getMqttBrokerUrl();
            System.out.println("[LoadBalancerApp] Using MQTT broker: " + brokerUrl);

            // Initialize MQTT Broker
            mqttBroker = new MqttBroker(brokerUrl, config.getServiceId());
            mqttBroker.connect();

            // Initialize Routing Service
            RoutingService routingService = new RoutingService();

            // Initialize Health Monitor and start heartbeat tracking
            healthMonitor = new HealthMonitor();
            // Register the initial static containers (matches Docker Compose service names)
            healthMonitor.registerContainer("fs-1");
            healthMonitor.registerContainer("fs-2");
            healthMonitor.registerContainer("fs-3");
            healthMonitor.registerContainer("fs-4");
            healthMonitor.registerContainer("agg-1");
            healthMonitor.start(mqttBroker);

            // Initialize Message Handler
            messageHandler = new MqttMessageHandler(mqttBroker, routingService, healthMonitor);
            
            // Also subscribe to delete responses
            mqttBroker.subscribe(TopicConstants.STORAGE_DELETE_RESPONSE, (topic, message) -> {
                String payload = new String(message.getPayload());
                System.out.println("[LoadBalancerApp] Received delete response: " + payload);
                messageHandler.handleDeleteResponse(payload);
            });
            mqttBroker.subscribe(TopicConstants.LEGACY_STORAGE_DELETE_RESPONSE, (topic, message) -> {
                messageHandler.handleDeleteResponse(new String(message.getPayload()));
            });

            // Start listening for incoming requests
            messageHandler.startListening();

            System.out.println("\n╔════════════════════════════════════════╗");
            System.out.println("║   LOAD BALANCER READY FOR REQUESTS     ║");
            System.out.println("╚════════════════════════════════════════╝\n");

            // Graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[LoadBalancerApp] Shutting down gracefully...");
                if (healthMonitor != null) {
                    healthMonitor.stop();
                }
                if (mqttBroker != null) {
                    mqttBroker.disconnect();
                }
                System.out.println("[LoadBalancerApp] Shutdown complete.");
            }));

            // Keep application running
            Thread.currentThread().join();

        } catch (MqttException e) {
            System.err.println("[LoadBalancerApp] MQTT Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            System.err.println("[LoadBalancerApp] Interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[LoadBalancerApp] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
