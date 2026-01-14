package com.example.loadbalancer.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates deletion across FS containers.
 * Tracks responses and confirms once all FS containers respond.
 */
public class DeleteCoordinator {

    private final Map<String, CountDownLatch> deleteLatches = new ConcurrentHashMap<>();
    private final Map<String, List<String>> deleteResponses = new ConcurrentHashMap<>();

    /**
     * Create a delete coordination for a specific file.
     * Waits for responses from all FS containers in each volume group.
     */
    public void initiateDelete(long fileId, int numFSContainers) {
        String key = "delete_" + fileId;
        deleteLatches.put(key, new CountDownLatch(numFSContainers));
        deleteResponses.put(key, Collections.synchronizedList(new ArrayList<>()));
        
        System.out.println("[DeleteCoordinator] Initiated delete for fileId=" + fileId + 
                         ", waiting for " + numFSContainers + " responses");
    }

    /**
     * Record a delete response from an FS container.
     */
    public void recordDeleteResponse(long fileId, String fsId, String status) {
        String key = "delete_" + fileId;
        CountDownLatch latch = deleteLatches.get(key);
        
        if (latch != null) {
            deleteResponses.get(key).add(fsId + ":" + status);
            latch.countDown();
            System.out.println("[DeleteCoordinator] Received delete response from " + fsId + 
                             " for fileId=" + fileId + " (remaining: " + latch.getCount() + ")");
        }
    }

    /**
     * Wait for all delete confirmations.
     * Returns true if all FS containers confirmed, false if timeout.
     */
    public boolean waitForDeleteCompletion(long fileId, long timeoutSeconds) {
        String key = "delete_" + fileId;
        CountDownLatch latch = deleteLatches.get(key);
        
        if (latch == null) {
            return false;
        }

        try {
            boolean completed = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (completed) {
                System.out.println("[DeleteCoordinator] All FS containers confirmed delete for fileId=" + fileId);
            } else {
                System.err.println("[DeleteCoordinator] Timeout waiting for delete confirmations for fileId=" + fileId);
            }
            return completed;
        } catch (InterruptedException e) {
            System.err.println("[DeleteCoordinator] Interrupted while waiting for delete: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // Cleanup
            deleteLatches.remove(key);
            deleteResponses.remove(key);
        }
    }

    /**
     * Get delete responses for debugging.
     */
    public List<String> getDeleteResponses(long fileId) {
        return deleteResponses.getOrDefault("delete_" + fileId, new ArrayList<>());
    }
}
