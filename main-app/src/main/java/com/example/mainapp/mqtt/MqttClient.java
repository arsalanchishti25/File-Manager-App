// src/main/java/com/example/mainapp/mqtt/MqttClient.java
package com.example.mainapp.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import com.google.gson.Gson;
// import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MQTT Client for Main App.
 * Handles communication with Load Balancer, Aggregator, and FS containers.
 */
public class MqttClient {

    private final String clientId;
    private final String brokerUrl;
    private IMqttClient mqttClient;
    private final Gson gson = new Gson();
    
    // Store pending responses: topic -> response payload
    private final Map<String, String> pendingResponses = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> responseLatchesMap = new ConcurrentHashMap<>();

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
        options.setKeepAliveInterval(30);
 
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
        message.setQos(1);
        message.setRetained(false);

        mqttClient.publish(topic, message);
        System.out.println("[MqttClient-" + clientId + "] Published to " + topic);
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
        responseLatchesMap.put(responseTopic, latch);

        // Subscribe to response topic
        subscribe(responseTopic, (topic, message) -> {
            String response = new String(message.getPayload());
            System.out.println("[MqttClient-" + clientId + "] Received response on " + topic);
            pendingResponses.put(responseTopic, response);
            
            CountDownLatch responseLatch = responseLatchesMap.get(responseTopic);
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
            responseLatchesMap.remove(responseTopic);
            return response;
        } else {
            System.err.println("[MqttClient-" + clientId + "] Timeout waiting for response on " + responseTopic);
            responseLatchesMap.remove(responseTopic);
            return null;
        }
    }

    /**
     * Subscribe to a topic and wait for a message (without sending a request first).
     * Used when Aggregator sends completion notifications.
     */
    public String waitForMessage(String topic, long timeoutMs) {
        final String[] receivedMessage = {null};
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            // Subscribe to topic
            mqttClient.subscribe(topic, 1, (t, message) -> {
                receivedMessage[0] = new String(message.getPayload());
                System.out.println("[MqttClient] Received message on " + t + ": " + receivedMessage[0]);
                latch.countDown();
            });

            // Wait for message
            boolean received = latch.await(timeoutMs, TimeUnit.MILLISECONDS);

            // Unsubscribe
            mqttClient.unsubscribe(topic);

            if (!received) {
                System.err.println("[MqttClient] Timeout waiting for message on " + topic);
                return null;
            }

            return receivedMessage[0];

        } catch (Exception e) {
            System.err.println("[MqttClient] Error waiting for message: " + e.getMessage());
            return null;
        }
    }
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public String getClientId() {
        return clientId;
    }

    public Gson getGson() {
        return gson;
    }
}
