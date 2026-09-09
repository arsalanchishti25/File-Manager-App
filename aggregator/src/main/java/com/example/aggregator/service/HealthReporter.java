package com.example.aggregator.service;

import com.example.aggregator.mqtt.MqttBroker;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publishes periodic MQTT heartbeats so the Load Balancer's HealthMonitor
 * can track whether this aggregator container is alive.
 *
 * Topic: heartbeat/{aggregatorId}
 * Interval: every 10 seconds
 */
public class HealthReporter {

    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;

    private final String aggregatorId;
    private final MqttBroker mqttBroker;
    private final ScheduledExecutorService scheduler;
    private final String topic;

    public HealthReporter(String aggregatorId, MqttBroker mqttBroker) {
        this.aggregatorId = aggregatorId;
        this.mqttBroker = mqttBroker;
        this.topic = "heartbeat/" + aggregatorId;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-reporter-" + aggregatorId);
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
            String payload = "{\"aggregatorId\":\"" + aggregatorId + "\"," +
                             "\"status\":\"HEALTHY\"," +
                             "\"timestamp\":" + System.currentTimeMillis() + "}";
            mqttBroker.publish(topic, payload);
        } catch (MqttException e) {
            System.err.println("[HealthReporter] Failed to publish heartbeat: " + e.getMessage());
        }
    }
}
