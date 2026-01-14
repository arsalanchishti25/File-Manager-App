// src/main/java/com/example/hostmanager/service/ContainerOrchestrator.java
package com.example.hostmanager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-level orchestration of container scaling operations.
 * Manages groups of containers and coordinates scaling decisions.
 */
public class ContainerOrchestrator {

    private final HostManager hostManager;
    private final String orchestratorId;

    // Track running containers: containerName -> containerInfo
    private final Map<String, ContainerInfo> runningContainers;

    // Auto-increment counters for generating unique container names
    private final AtomicInteger fsContainerCounter;
    private final AtomicInteger aggContainerCounter;

    // Base ports for SFTP (incremented for each new container)
    private static final int FS_BASE_SFTP_PORT = 3000;
    private static final int AGG_BASE_SFTP_PORT = 4000;

    public ContainerOrchestrator(String orchestratorId, HostManager hostManager) {
        this.orchestratorId = orchestratorId;
        this.hostManager = hostManager;
        this.runningContainers = new ConcurrentHashMap<>();
        this.fsContainerCounter = new AtomicInteger(0);
        this.aggContainerCounter = new AtomicInteger(0);

        System.out.println("[" + orchestratorId + "] ContainerOrchestrator initialized");
    }

    /**
     * Scale UP File Storage containers by adding 4 new containers (one per volume group).
     * 
     * @return List of newly created container names
     */
    public List<String> scaleUpFileStorage() {
        System.out.println("[" + orchestratorId + "] Scaling UP File Storage containers (adding 4 containers)...");

        List<String> newContainers = new ArrayList<>();

        // Create 4 containers, one for each volume group (1-4)
        for (int volumeGroup = 1; volumeGroup <= 4; volumeGroup++) {
            int containerNum = fsContainerCounter.incrementAndGet();
            String containerName = "fs-" + containerNum;
            int sftpPort = FS_BASE_SFTP_PORT + containerNum;
            String volumePath = "/var/filemanager/volumes/volume" + volumeGroup + "/" + containerName;

            // Start the container
            String containerId = hostManager.startFileStorageContainer(
                containerName, 
                volumeGroup, 
                sftpPort, 
                volumePath
            );

            if (containerId != null) {
                // Get container IP
                String containerIP = hostManager.getContainerIP(containerName);

                // Track the new container
                ContainerInfo info = new ContainerInfo(
                    containerName,
                    containerId,
                    "FILE_STORAGE",
                    containerIP != null ? containerIP : "unknown",
                    sftpPort,
                    volumeGroup,
                    "HEALTHY"
                );

                runningContainers.put(containerName, info);
                newContainers.add(containerName);

                System.out.println("[" + orchestratorId + "] Created " + containerName 
                                   + " (Volume Group " + volumeGroup + ", Port " + sftpPort + ")");

                // TODO: Register container in MySQL database
                // registerContainerInDB(info);

                // TODO: Publish MQTT message to Load Balancer about new container
                // mqttClient.publish("hostmanager/container/new", info.toJson());
            } else {
                System.err.println("[" + orchestratorId + "] Failed to create " + containerName);
            }
        }

        System.out.println("[" + orchestratorId + "] Scale UP complete. Created " + newContainers.size() + " containers.");
        return newContainers;
    }

    /**
     * Scale DOWN File Storage containers by removing 4 containers (one per volume group).
     * Removes the most recently added containers.
     * 
     * @return List of removed container names
     */
    public List<String> scaleDownFileStorage() {
        System.out.println("[" + orchestratorId + "] Scaling DOWN File Storage containers (removing 4 containers)...");

        List<String> removedContainers = new ArrayList<>();

        // Find the highest numbered FS container for each volume group and remove it
        for (int volumeGroup = 1; volumeGroup <= 4; volumeGroup++) {
            String containerToRemove = findNewestFSContainer(volumeGroup);

            if (containerToRemove != null) {
                boolean stopped = hostManager.stopContainer(containerToRemove);

                if (stopped) {
                    @SuppressWarnings("unused")
                    ContainerInfo info = runningContainers.remove(containerToRemove);
                    removedContainers.add(containerToRemove);

                    System.out.println("[" + orchestratorId + "] Removed " + containerToRemove 
                                       + " (Volume Group " + volumeGroup + ")");

                    // TODO: Update MySQL database to mark container as STOPPED
                    // markContainerStoppedInDB(containerToRemove);

                    // TODO: Publish MQTT message to Load Balancer about removed container
                    // mqttClient.publish("hostmanager/container/removed", "{\"name\":\"" + containerToRemove + "\"}");
                } else {
                    System.err.println("[" + orchestratorId + "] Failed to remove " + containerToRemove);
                }
            } else {
                System.out.println("[" + orchestratorId + "] No FS container found for Volume Group " + volumeGroup);
            }
        }

        System.out.println("[" + orchestratorId + "] Scale DOWN complete. Removed " + removedContainers.size() + " containers.");
        return removedContainers;
    }

    /**
     * Scale UP Aggregator containers by adding 1 new container.
     * 
     * @return Name of newly created container, or null if failed
     */
    public String scaleUpAggregator() {
        System.out.println("[" + orchestratorId + "] Scaling UP Aggregator containers (adding 1 container)...");

        int containerNum = aggContainerCounter.incrementAndGet();
        String containerName = "agg-" + containerNum;
        int sftpPort = AGG_BASE_SFTP_PORT + containerNum;
        String workingDir = "/var/filemanager/aggregator/" + containerName;

        String containerId = hostManager.startAggregatorContainer(containerName, sftpPort, workingDir);

        if (containerId != null) {
            String containerIP = hostManager.getContainerIP(containerName);

            ContainerInfo info = new ContainerInfo(
                containerName,
                containerId,
                "AGGREGATOR",
                containerIP != null ? containerIP : "unknown",
                sftpPort,
                0,  // Aggregators don't have volume groups
                "HEALTHY"
            );

            runningContainers.put(containerName, info);

            System.out.println("[" + orchestratorId + "] Created " + containerName + " (Port " + sftpPort + ")");

            // TODO: Register container in MySQL database
            // registerContainerInDB(info);

            // TODO: Publish MQTT message to Load Balancer
            // mqttClient.publish("hostmanager/container/new", info.toJson());

            return containerName;
        } else {
            System.err.println("[" + orchestratorId + "] Failed to create " + containerName);
            return null;
        }
    }

    /**
     * Scale DOWN Aggregator containers by removing 1 container.
     * 
     * @return Name of removed container, or null if none available
     */
    public String scaleDownAggregator() {
        System.out.println("[" + orchestratorId + "] Scaling DOWN Aggregator containers (removing 1 container)...");

        String containerToRemove = findNewestAggregator();

        if (containerToRemove != null) {
            boolean stopped = hostManager.stopContainer(containerToRemove);

            if (stopped) {
                runningContainers.remove(containerToRemove);
                System.out.println("[" + orchestratorId + "] Removed " + containerToRemove);

                // TODO: Update MySQL database
                // markContainerStoppedInDB(containerToRemove);

                // TODO: Publish MQTT message
                // mqttClient.publish("hostmanager/container/removed", "{\"name\":\"" + containerToRemove + "\"}");

                return containerToRemove;
            } else {
                System.err.println("[" + orchestratorId + "] Failed to remove " + containerToRemove);
                return null;
            }
        } else {
            System.out.println("[" + orchestratorId + "] No Aggregator containers to remove");
            return null;
        }
    }

    /**
     * Find the newest (highest numbered) FS container for a given volume group.
     */
    private String findNewestFSContainer(int volumeGroup) {
        return runningContainers.values().stream()
            .filter(c -> c.type.equals("FILE_STORAGE") && c.volumeGroup == volumeGroup)
            .map(c -> c.name)
            .max((a, b) -> {
                int numA = Integer.parseInt(a.substring(3)); // "fs-5" -> 5
                int numB = Integer.parseInt(b.substring(3));
                return Integer.compare(numA, numB);
            })
            .orElse(null);
    }

    /**
     * Find the newest Aggregator container.
     */
    private String findNewestAggregator() {
        return runningContainers.values().stream()
            .filter(c -> c.type.equals("AGGREGATOR"))
            .map(c -> c.name)
            .max((a, b) -> {
                int numA = Integer.parseInt(a.substring(4)); // "agg-2" -> 2
                int numB = Integer.parseInt(b.substring(4));
                return Integer.compare(numA, numB);
            })
            .orElse(null);
    }

    /**
     * Get list of all running containers.
     */
    public List<ContainerInfo> getRunningContainers() {
        return new ArrayList<>(runningContainers.values());
    }

    /**
     * Get count of running containers by type.
     */
    public int getContainerCount(String type) {
        return (int) runningContainers.values().stream()
            .filter(c -> c.type.equals(type))
            .count();
    }

    /**
     * Simple data class to hold container information.
     */
    public static class ContainerInfo {
        public final String name;
        public final String containerId;
        public final String type;  // "FILE_STORAGE" or "AGGREGATOR"
        public final String ip;
        public final int sftpPort;
        public final int volumeGroup;  // 1-4 for FS, 0 for Aggregator
        public final String status;

        public ContainerInfo(String name, String containerId, String type, String ip, 
                             int sftpPort, int volumeGroup, String status) {
            this.name = name;
            this.containerId = containerId;
            this.type = type;
            this.ip = ip;
            this.sftpPort = sftpPort;
            this.volumeGroup = volumeGroup;
            this.status = status;
        }

        @Override
        public String toString() {
            return name + " (" + type + ", VG:" + volumeGroup + ", " + ip + ":" + sftpPort + ", " + status + ")";
        }
    }
}
