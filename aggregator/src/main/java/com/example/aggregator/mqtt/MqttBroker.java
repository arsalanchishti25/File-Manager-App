package com.example.aggregator.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import com.google.gson.Gson;

/**
 * MQTT Client for Aggregator.
 */
public class MqttBroker {

    private final String brokerUrl;
    private final String clientId;
    private IMqttClient mqttClient;
    private final Gson gson = new Gson();

    public MqttBroker(String brokerUrl, String clientId) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
    }

    public void connect() throws MqttException {
        mqttClient = new org.eclipse.paho.client.mqttv3.MqttClient(
                brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        mqttClient.connect(options);
        System.out.println("[MqttBroker] Connected to: " + brokerUrl);
    }

    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                System.out.println("[MqttBroker] Disconnected");
            }
        } catch (MqttException e) {
            System.err.println("[MqttBroker] Disconnect error: " + e.getMessage());
        }
    }

    public void subscribe(String topic, IMqttMessageListener listener) throws MqttException {
        if (!mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }

        mqttClient.subscribe(topic, 1, listener);
        System.out.println("[MqttBroker] Subscribed to: " + topic);
    }

    public void publish(String topic, String payload) throws MqttException {
        if (!mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        message.setRetained(false);

        mqttClient.publish(topic, message);
        System.out.println("[MqttBroker] Published to " + topic);
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public Gson getGson() {
        return gson;
    }
}
