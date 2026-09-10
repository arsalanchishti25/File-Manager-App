package com.example.loadbalancer.config;

import java.util.Collections;
import java.util.Map;

/**
 * Centralized configuration for load-balancer.
 * Supports map injection for testability.
 */
public class LoadBalancerConfig {

    private final String serviceId;
    private final String mqttBrokerUrl;

    public LoadBalancerConfig(Map<String, String> env) {
        Map<String, String> source = env != null ? env : Collections.emptyMap();

        String id = source.get("SERVICE_ID");
        if (id == null || id.isBlank()) {
            id = source.get("MQTT_CLIENT_ID");
        }
        this.serviceId = (id != null && !id.isBlank()) ? id.trim() : "load-balancer";

        String broker = source.get("MQTT_BROKER_URL");
        this.mqttBrokerUrl = (broker != null && !broker.isBlank())
                ? broker.trim()
                : "tcp://filemanager-mqtt:1883";
    }

    public static LoadBalancerConfig fromEnv() {
        return new LoadBalancerConfig(System.getenv());
    }

    public static LoadBalancerConfig fromMap(Map<String, String> env) {
        return new LoadBalancerConfig(env);
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getMqttBrokerUrl() {
        return mqttBrokerUrl;
    }
}
