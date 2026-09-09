package com.example.loadbalancer.service;

import com.example.loadbalancer.model.AggregatorInfo;
import com.example.loadbalancer.model.FSContainerInfo;
import java.util.*;

/**
 * Handles routing decisions for file operations.
 *
 * Container hostnames and ports match the Docker Compose service names exactly:
 *   - file-storage-1 .. file-storage-4  (SFTP ports 2201-2204)
 *   - aggregator                         (SFTP port 2222)
 *
 * The HealthMonitor is consulted before selecting containers so that
 * OFFLINE containers are skipped.
 */
public class RoutingService {

    private final AggregatorInfo aggregator;
    private final List<FSContainerInfo> fsContainers;

    public RoutingService() {
        // Hostnames match Docker Compose service names.
        // SFTP ports match the SFTP_PORT env vars set in docker-compose.yml.
        this.aggregator = new AggregatorInfo("agg-1", "aggregator", 2222);

        this.fsContainers = new ArrayList<>();
        // id        = FS_CONTAINER_ID env var value (used in heartbeat and delete topics)
        // ip        = Docker Compose service hostname (DNS-resolvable on filemanager-network)
        // port      = SFTP_PORT env var value
        // volumeGroup = which of the 4 volume groups this container holds
        fsContainers.add(new FSContainerInfo("fs-1", "file-storage-1", 2201, 1));
        fsContainers.add(new FSContainerInfo("fs-2", "file-storage-2", 2202, 2));
        fsContainers.add(new FSContainerInfo("fs-3", "file-storage-3", 2203, 3));
        fsContainers.add(new FSContainerInfo("fs-4", "file-storage-4", 2204, 4));

        System.out.println("[RoutingService] Initialized with Docker Compose service hostnames:");
        System.out.println("  Aggregator: " + aggregator);
        for (FSContainerInfo fs : fsContainers) {
            System.out.println("  FS Container: " + fs);
        }
    }

    /**
     * Select aggregator for file operations.
     * Returns the single aggregator (future: round-robin across multiple).
     */
    public AggregatorInfo selectAggregator() {
        System.out.println("[RoutingService] Selected aggregator: " + aggregator.getId());
        return aggregator;
    }

    /**
     * Select one FS container per volume group (4 total).
     * Skips containers that are OFFLINE according to the HealthMonitor.
     * Falls back to the container even if offline (with a warning) so the
     * system degrades gracefully rather than hard-failing on routing.
     */
    public List<FSContainerInfo> selectFSContainers() {
        return selectFSContainers(null);
    }

    /**
     * Select one FS container per volume group, health-aware.
     *
     * @param healthMonitor may be null (skips health check, selects any available)
     */
    public List<FSContainerInfo> selectFSContainers(
            com.example.loadbalancer.service.HealthMonitor healthMonitor) {

        List<FSContainerInfo> selected = new ArrayList<>();

        for (int vg = 1; vg <= 4; vg++) {
            final int volumeGroup = vg;

            // Prefer a healthy container; fall back to any container in the group
            FSContainerInfo fs = fsContainers.stream()
                    .filter(f -> f.getVolumeGroup() == volumeGroup)
                    .filter(f -> healthMonitor == null || healthMonitor.isHealthy(f.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        // No healthy container — fall back to first available with a warning
                        FSContainerInfo fallback = fsContainers.stream()
                                .filter(f -> f.getVolumeGroup() == volumeGroup)
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException(
                                        "No FS container configured for volume group " + volumeGroup));
                        System.err.println("[RoutingService] WARNING: No healthy FS container for " +
                                           "volume group " + volumeGroup +
                                           ". Using offline fallback: " + fallback.getId());
                        return fallback;
                    });

            selected.add(fs);
        }

        System.out.println("[RoutingService] Selected FS containers:");
        for (FSContainerInfo fs : selected) {
            System.out.println("  " + fs);
        }

        return selected;
    }

    /**
     * Get all FS containers in a specific volume group.
     * Used by delete coordinator to broadcast delete commands.
     */
    public List<FSContainerInfo> getFSContainersInVolumeGroup(int volumeGroup) {
        return fsContainers.stream()
                .filter(f -> f.getVolumeGroup() == volumeGroup)
                .toList();
    }

    /**
     * Get all known FS containers regardless of health or volume group.
     */
    public List<FSContainerInfo> getAllFSContainers() {
        return Collections.unmodifiableList(fsContainers);
    }
}
