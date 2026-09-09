package com.example.loadbalancer.service;

import com.example.loadbalancer.mqtt.MqttBroker;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors container health via MQTT heartbeats.
 *
 * Each file-storage and aggregator container publishes a heartbeat message
 * every ~10 seconds to the topic: heartbeat/{containerId}
 *
 * HealthMonitor subscribes to heartbeat/# and tracks the last-seen time
 * for every known container. A container is marked OFFLINE if no heartbeat
 * is received within OFFLINE_THRESHOLD_SECONDS (30s).
 *
 * The RoutingService queries isHealthy() before selecting containers.
 */
public class HealthMonitor {

    /** Seconds without a heartbeat before a container is considered OFFLINE. */
    private static final int OFFLINE_THRESHOLD_SECONDS = 30;

    /** How often the staleness checker runs (seconds). */
    private static final int CHECK_INTERVAL_SECONDS = 10;

    // containerId -> epoch-second of last received heartbeat
    private final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();

    // Containers that have been registered (we know about them)
    private final Set<String> registeredContainers = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "health-monitor");
                t.setDaemon(true);
                return t;
            });

    /**
     * Register a container so HealthMonitor knows to track it.
     * A registered container is OFFLINE until its first heartbeat arrives.
     *
     * @param containerId the container's unique ID (e.g. "fs-1", "agg-1")
     */
    public void registerContainer(String containerId) {
        registeredContainers.add(containerId);
        System.out.println("[HealthMonitor] Registered container: " + containerId);
    }

    /**
     * Start the MQTT heartbeat subscription and the periodic staleness checker.
     *
     * @param mqttBroker connected MqttBroker instance
     */
    public void start(MqttBroker mqttBroker) {
        // Subscribe to all heartbeat topics: heartbeat/{containerId}
        try {
            mqttBroker.subscribe("heartbeat/#", (topic, message) -> {
                // topic format: "heartbeat/fs-1", "heartbeat/agg-1", etc.
                String containerId = topic.substring("heartbeat/".length());
                recordHeartbeat(containerId);
            });
            System.out.println("[HealthMonitor] Subscribed to heartbeat/# topic");
        } catch (MqttException e) {
            System.err.println("[HealthMonitor] Failed to subscribe to heartbeats: " + e.getMessage());
        }

        // Periodically log which containers are offline
        scheduler.scheduleAtFixedRate(this::logOfflineContainers,
                CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);

        System.out.println("[HealthMonitor] Started. Offline threshold: " +
                           OFFLINE_THRESHOLD_SECONDS + "s, check interval: " +
                           CHECK_INTERVAL_SECONDS + "s");
    }

    /**
     * Stop the staleness checker thread.
     */
    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * Record a heartbeat from a container. Also registers it if previously unknown.
     *
     * @param containerId the container that sent a heartbeat
     */
    public void recordHeartbeat(String containerId) {
        boolean isNew = !registeredContainers.contains(containerId);
        registeredContainers.add(containerId);
        lastHeartbeat.put(containerId, Instant.now().getEpochSecond());

        if (isNew) {
            System.out.println("[HealthMonitor] First heartbeat from new container: " + containerId);
        }
    }

    /**
     * Returns true if the container has sent a heartbeat within the offline threshold.
     *
     * A registered container with no heartbeat yet is considered OFFLINE.
     *
     * @param containerId the container to check
     * @return true if HEALTHY, false if OFFLINE or unknown
     */
    public boolean isHealthy(String containerId) {
        Long lastSeen = lastHeartbeat.get(containerId);
        if (lastSeen == null) {
            return false;  // Never received a heartbeat — treat as offline
        }
        long ageSeconds = Instant.now().getEpochSecond() - lastSeen;
        return ageSeconds <= OFFLINE_THRESHOLD_SECONDS;
    }

    /**
     * Returns the status string for a container: "HEALTHY", "OFFLINE", or "UNKNOWN".
     *
     * @param containerId the container to check
     */
    public String getStatus(String containerId) {
        if (!registeredContainers.contains(containerId)) {
            return "UNKNOWN";
        }
        return isHealthy(containerId) ? "HEALTHY" : "OFFLINE";
    }

    /**
     * Returns an unmodifiable view of all registered container IDs.
     */
    public Set<String> getRegisteredContainers() {
        return Collections.unmodifiableSet(registeredContainers);
    }

    /**
     * Logs all registered containers that are currently OFFLINE.
     * Called periodically by the scheduler.
     */
    private void logOfflineContainers() {
        long now = Instant.now().getEpochSecond();
        for (String id : registeredContainers) {
            Long lastSeen = lastHeartbeat.get(id);
            if (lastSeen == null) {
                System.out.println("[HealthMonitor] OFFLINE (no heartbeat yet): " + id);
            } else {
                long ageSeconds = now - lastSeen;
                if (ageSeconds > OFFLINE_THRESHOLD_SECONDS) {
                    System.out.println("[HealthMonitor] OFFLINE (" + ageSeconds + "s since last heartbeat): " + id);
                }
            }
        }
    }
}
