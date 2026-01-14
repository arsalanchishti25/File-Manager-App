package com.example.loadbalancer;

import com.example.loadbalancer.mqtt.MqttBroker;
import com.example.loadbalancer.mqtt.MqttMessageHandler;
import com.example.loadbalancer.service.RoutingService;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Load Balancer Main Application.
 * Orchestrates file operations via MQTT.
 */
public class LoadBalancerApp {

    private static MqttBroker mqttBroker;
    private static MqttMessageHandler messageHandler;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║        LOAD BALANCER STARTING          ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        try {
            // Get MQTT broker URL from environment or use default
            String brokerUrl = System.getenv("MQTT_BROKER_URL");
            if (brokerUrl == null) {
                brokerUrl = "tcp://filemanager-mqtt:1883";  // Docker container hostname
                System.out.println("[LoadBalancerApp] Using default MQTT broker: " + brokerUrl);
            } else {
                System.out.println("[LoadBalancerApp] Using MQTT broker: " + brokerUrl);
            }

            // Initialize MQTT Broker
            mqttBroker = new MqttBroker(brokerUrl, "load-balancer");
            mqttBroker.connect();

            // Initialize Routing Service
            RoutingService routingService = new RoutingService();

            // Initialize Message Handler
            messageHandler = new MqttMessageHandler(mqttBroker, routingService);
            
            // Also subscribe to delete responses
            mqttBroker.subscribe("fs/delete/response", (topic, message) -> {
                String payload = new String(message.getPayload());
                System.out.println("[LoadBalancerApp] Received delete response: " + payload);
                messageHandler.handleDeleteResponse(payload);
            });

            // Start listening for incoming requests
            messageHandler.startListening();

            System.out.println("\n╔════════════════════════════════════════╗");
            System.out.println("║   LOAD BALANCER READY FOR REQUESTS     ║");
            System.out.println("╚════════════════════════════════════════╝\n");

            // Graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[LoadBalancerApp] Shutting down gracefully...");
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
