package com.example.hostmanager.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MQTT Client for Main App.
 * Handles communication with Load Balancer via MQTT broker.
 */
public class MqttClient {

    private final String clientId;
    private final String brokerUrl;
    private IMqttClient mqttClient;
    
    // Store pending responses: topic -> response payload
    private final Map<String, String> pendingResponses = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> responsLatches = new ConcurrentHashMap<>();

    public MqttClient(String brokerUrl, String clientId) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
    }

    /**
     * Connect to MQTT broker.
     */
    public void connect() throws MqttException {
        mqttClient = new org.eclipse.paho.client.mqttv3.MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        mqttClient.connect(options);
        System.out.println("[MqttClient-" + clientId + "] Connected to broker: " + brokerUrl);
    }

    /**
     * Disconnect from MQTT broker.
     */
    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                System.out.println("[MqttClient-" + clientId + "] Disconnected");
            }
        } catch (MqttException e) {
            System.err.println("[MqttClient-" + clientId + "] Disconnect error: " + e.getMessage());
        }
    }

    /**
     * Publish a message to a topic.
     */
    public void publish(String topic, String payload) throws MqttException {
        if (!mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1); // At least once delivery
        message.setRetained(false);

        mqttClient.publish(topic, message);
        System.out.println("[MqttClient-" + clientId + "] Published to " + topic + ": " + payload);
    }

    /**
     * Subscribe to a topic with a callback.
     */
    public void subscribe(String topic, IMqttMessageListener listener) throws MqttException {
        if (!mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }

        mqttClient.subscribe(topic, 1, listener);
        System.out.println("[MqttClient-" + clientId + "] Subscribed to: " + topic);
    }

    /**
     * Publish a message and wait for a response on a specific topic.
     * 
     * @param requestTopic Topic to publish request
     * @param responseTopic Topic to listen for response
     * @param payload Request payload
     * @param timeoutMs Timeout in milliseconds
     * @return Response payload, or null if timeout
     */
    public String publishAndWaitForResponse(String requestTopic, String responseTopic, 
                                           String payload, long timeoutMs) throws MqttException, InterruptedException {
        // Setup latch for response
        CountDownLatch latch = new CountDownLatch(1);
        responsLatches.put(responseTopic, latch);

        // Subscribe to response topic
        subscribe(responseTopic, (topic, message) -> {
            String response = new String(message.getPayload());
            System.out.println("[MqttClient-" + clientId + "] Received response on " + topic + ": " + response);
            pendingResponses.put(topic, response);
            
            CountDownLatch responseLatch = responsLatches.get(topic);
            if (responseLatch != null) {
                responseLatch.countDown();
            }
        });

        // Publish request
        publish(requestTopic, payload);

        // Wait for response
        boolean received = latch.await(timeoutMs, TimeUnit.MILLISECONDS);

        if (received) {
            String response = pendingResponses.remove(responseTopic);
            responsLatches.remove(responseTopic);
            return response;
        } else {
            System.err.println("[MqttClient-" + clientId + "] Timeout waiting for response on " + responseTopic);
            responsLatches.remove(responseTopic);
            return null;
        }
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public String getClientId() {
        return clientId;
    }
}
