package com.example.filestorage.service;

import com.example.filestorage.mqtt.MqttBroker;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publishes periodic MQTT heartbeats so the Load Balancer's HealthMonitor
 * can track whether this file-storage container is alive.
 *
 * Topic: heartbeat/{containerId}
 * Interval: every 10 seconds
 *
 * The Load Balancer marks a container OFFLINE if no heartbeat is received
 * within 30 seconds, so this interval provides a comfortable margin.
 */
public class HealthReporter {

    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;

    private final String containerId;
    private final MqttBroker mqttBroker;
    private final ScheduledExecutorService scheduler;
    private final String topic;

    public HealthReporter(String containerId, MqttBroker mqttBroker) {
        this.containerId = containerId;
        this.mqttBroker = mqttBroker;
        this.topic = "heartbeat/" + containerId;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-reporter-" + containerId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start publishing heartbeats. First heartbeat fires immediately,
     * then repeats every HEARTBEAT_INTERVAL_SECONDS.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::publishHeartbeat,
                0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        System.out.println("[HealthReporter] Started heartbeat publishing on topic: " + topic);
    }

    /**
     * Stop heartbeat publishing.
     */
    public void stop() {
        scheduler.shutdownNow();
        System.out.println("[HealthReporter] Stopped heartbeat publishing");
    }

    private void publishHeartbeat() {
        try {
            String payload = "{\"containerId\":\"" + containerId + "\"," +
                             "\"status\":\"HEALTHY\"," +
                             "\"timestamp\":" + System.currentTimeMillis() + "}";
            mqttBroker.publish(topic, payload);
        } catch (MqttException e) {
            // Don't crash the thread — MQTT may temporarily disconnect; auto-reconnect handles it
            System.err.println("[HealthReporter] Failed to publish heartbeat: " + e.getMessage());
        }
    }
}
