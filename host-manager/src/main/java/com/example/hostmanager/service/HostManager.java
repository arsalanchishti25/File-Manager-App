// src/main/java/com/example/hostmanager/service/HostManager.java
package com.example.hostmanager.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
// import java.sql.Connection;
// import java.sql.PreparedStatement;
// import java.sql.SQLException;
// import java.time.LocalDateTime;

/**
 * Handles low-level Docker container operations using ProcessBuilder.
 * Manages starting, stopping, and checking status of individual containers.
 */
public class HostManager {

    private final String hostId;

    public HostManager(String hostId) {
        this.hostId = hostId;
        System.out.println("[" + hostId + "] HostManager initialized");
    }

    /**
     * Start a new File Storage container using Docker.
     * 
     * @param containerName Unique name for the container (e.g., "fs-1")
     * @param volumeGroup Volume group (1-4) this container belongs to
     * @param sftpPort SFTP port to expose (e.g., 2222)
     * @param volumePath Path on host for persistent storage
     * @return Container ID if successful, null if failed
     */
    public String startFileStorageContainer(String containerName, int volumeGroup, int sftpPort, String volumePath) {
        System.out.println("[" + hostId + "] Starting File Storage container: " + containerName);
        System.out.println("  Volume Group: " + volumeGroup);
        System.out.println("  SFTP Port: " + sftpPort);
        System.out.println("  Volume Path: " + volumePath);

        // Docker command to start FS container
        String[] dockerCmd = {
            "docker", "run", "-d",
            "--name", containerName,
            "-p", sftpPort + ":2222",  // Map host port to container SFTP port
            "-v", volumePath + ":/data/volume",  // Mount volume
            "-e", "CONTAINER_ID=" + containerName,
            "-e", "VOLUME_GROUP=" + volumeGroup,
            "-e", "MQTT_BROKER_URL=tcp://filemanager-mqtt:1883",
            "file-storage:latest"  // Docker image name (must be built beforehand)
        };

        try {
            String containerId = executeDockerCommand(dockerCmd);
            
            if (containerId != null && !containerId.isEmpty()) {
                System.out.println("[" + hostId + "] Successfully started " + containerName 
                                   + " (Container ID: " + containerId.substring(0, 12) + ")");
                return containerId;
            } else {
                System.err.println("[" + hostId + "] Failed to start " + containerName + ": No container ID returned");
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("[" + hostId + "] Error starting " + containerName + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Start a new Aggregator container using Docker.
     * 
     * @param containerName Unique name for the container (e.g., "agg-1")
     * @param sftpPort SFTP port to expose
     * @param workingDir Path on host for working directory
     * @return Container ID if successful, null if failed
     */
    public String startAggregatorContainer(String containerName, int sftpPort, String workingDir) {
        System.out.println("[" + hostId + "] Starting Aggregator container: " + containerName);
        System.out.println("  SFTP Port: " + sftpPort);
        System.out.println("  Working Dir: " + workingDir);

        String[] dockerCmd = {
            "docker", "run", "-d",
            "--name", containerName,
            "-p", sftpPort + ":2222",
            "-v", workingDir + ":/data/aggregator",
            "-e", "AGGREGATOR_ID=" + containerName,
            "-e", "MQTT_BROKER_URL=tcp://filemanager-mqtt:1883",
            "aggregator:latest"
        };

        try {
            String containerId = executeDockerCommand(dockerCmd);
            
            if (containerId != null && !containerId.isEmpty()) {
                System.out.println("[" + hostId + "] Successfully started " + containerName 
                                   + " (Container ID: " + containerId.substring(0, 12) + ")");
                return containerId;
            } else {
                System.err.println("[" + hostId + "] Failed to start " + containerName);
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("[" + hostId + "] Error starting " + containerName + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Stop and remove a running container.
     * 
     * @param containerName Name of the container to stop
     * @return true if successfully stopped, false otherwise
     */
    public boolean stopContainer(String containerName) {
        System.out.println("[" + hostId + "] Stopping container: " + containerName);

        String[] dockerCmd = {
            "docker", "rm", "-f", containerName  // Force remove (stops and removes)
        };

        try {
            String output = executeDockerCommand(dockerCmd);
            
            if (output != null && output.contains(containerName)) {
                System.out.println("[" + hostId + "] Successfully stopped " + containerName);
                return true;
            } else {
                System.err.println("[" + hostId + "] Failed to stop " + containerName);
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("[" + hostId + "] Error stopping " + containerName + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if a container is running.
     * 
     * @param containerName Name of the container to check
     * @return true if running, false otherwise
     */
    public boolean isContainerRunning(String containerName) {
        String[] dockerCmd = {
            "docker", "ps", "--filter", "name=" + containerName, "--format", "{{.Names}}"
        };

        try {
            String output = executeDockerCommand(dockerCmd);
            return output != null && output.trim().equals(containerName);
        } catch (Exception e) {
            System.err.println("[" + hostId + "] Error checking container status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the IP address of a running container.
     * 
     * @param containerName Name of the container
     * @return IP address or null if not found
     */
    public String getContainerIP(String containerName) {
        String[] dockerCmd = {
            "docker", "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", 
            containerName
        };

        try {
            String ip = executeDockerCommand(dockerCmd);
            return (ip != null && !ip.isEmpty()) ? ip.trim() : null;
        } catch (Exception e) {
            System.err.println("[" + hostId + "] Error getting container IP: " + e.getMessage());
            return null;
        }
    }

    /**
     * Execute a Docker command using ProcessBuilder.
     * 
     * @param command Docker command as string array
     * @return Command output (stdout)
     * @throws Exception if command execution fails
     */
    private String executeDockerCommand(String[] command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);  // Merge stderr into stdout

        System.out.println("[" + hostId + "] Executing: " + String.join(" ", command));

        Process process = pb.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("[" + hostId + "] Command failed with exit code: " + exitCode);
            System.err.println("[" + hostId + "] Output: " + output.toString());
            throw new RuntimeException("Docker command failed with exit code " + exitCode);
        }

        return output.toString().trim();
    }

    /**
     * Test Docker availability.
     * 
     * @return true if Docker is available, false otherwise
     */
    public boolean testDockerAvailability() {
        try {
            String[] dockerCmd = {"docker", "--version"};
            String output = executeDockerCommand(dockerCmd);
            System.out.println("[" + hostId + "] Docker version: " + output);
            return true;
        } catch (Exception e) {
            System.err.println("[" + hostId + "] Docker is not available: " + e.getMessage());
            return false;
        }
    }

    public String getHostId() {
        return hostId;
    }
}
