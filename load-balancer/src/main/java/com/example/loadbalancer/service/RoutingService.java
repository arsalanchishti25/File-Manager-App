package com.example.loadbalancer.service;

import com.example.loadbalancer.model.AggregatorInfo;
import com.example.loadbalancer.model.FSContainerInfo;
import java.util.*;

/**
 * Handles routing decisions for file operations.
 * Currently uses hardcoded values.
 * TODO: Implement actual load balancing logic
 */
public class RoutingService {

    private final AggregatorInfo aggregator;
    private final List<FSContainerInfo> fsContainers;

    public RoutingService() {
        // TODO: Load from configuration or service discovery
        this.aggregator = new AggregatorInfo("agg-1", "aggregator", 2222);
        
        this.fsContainers = new ArrayList<>();
        fsContainers.add(new FSContainerInfo("fs-1", "fs-1", 2222, 1));
        fsContainers.add(new FSContainerInfo("fs-2", "fs-2", 2222, 2));
        fsContainers.add(new FSContainerInfo("fs-3", "fs-3", 2222, 3));
        fsContainers.add(new FSContainerInfo("fs-4", "fs-4", 2222, 4));

        System.out.println("[RoutingService] Initialized with hardcoded containers");
        System.out.println("  Aggregator: " + aggregator);
        for (FSContainerInfo fs : fsContainers) {
            System.out.println("  FS Container: " + fs);
        }
    }

    /**
     * Select aggregator for file operations.
     * TODO: Implement round-robin selection from multiple aggregators
     */
    public AggregatorInfo selectAggregator() {
        System.out.println("[RoutingService] Selected aggregator: " + aggregator.getId());
        return aggregator;
    }

    /**
     * Select 4 FS containers (one per volume group).
     * TODO: Implement least-loaded selection logic
     */
    public List<FSContainerInfo> selectFSContainers() {
        List<FSContainerInfo> selected = new ArrayList<>();
        
        // Select one FS per volume group (currently hardcoded)
        for (int vg = 1; vg <= 4; vg++) {
            final int volumeGroup = vg;  // Make effectively final for lambda
            
            FSContainerInfo fs = fsContainers.stream()
                    .filter(f -> f.getVolumeGroup() == volumeGroup)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No FS container for volume group " + volumeGroup));
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
     * Used for delete broadcast.
     * TODO: Implement dynamic volume group tracking
     */
    public List<FSContainerInfo> getFSContainersInVolumeGroup(int volumeGroup) {
        return fsContainers.stream()
                .filter(f -> f.getVolumeGroup() == volumeGroup)
                .toList();
    }
}
