package com.example.loadbalancer.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Coordinates delete acknowledgements without blocking an MQTT callback thread.
 */
public class DeleteCoordinator {

    private final ConcurrentMap<String, PendingDelete> pendingDeletes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "delete-coordinator-timeout");
                thread.setDaemon(true);
                return thread;
            });

    public void registerDelete(long fileId, String operationId, String correlationId,
                               String mainAppId,
                               JsonArray chunks, long timeoutSeconds,
                               Consumer<DeleteResult> completion) {
        if (fileId <= 0 || isBlank(operationId) || isBlank(correlationId)
                || isBlank(mainAppId)
                || chunks == null || chunks.size() == 0 || completion == null) {
            throw new IllegalArgumentException("Invalid delete coordination request");
        }

        Set<Target> expectedTargets = new HashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            JsonObject chunk = chunks.get(i).getAsJsonObject();
            if (!chunk.has("fsId") || !chunk.has("chunkOrder")
                    || chunk.get("fsId").isJsonNull() || chunk.get("chunkOrder").isJsonNull()) {
                throw new IllegalArgumentException("Invalid delete chunk metadata");
            }
            expectedTargets.add(new Target(chunk.get("fsId").getAsString(),
                    chunk.get("chunkOrder").getAsInt()));
        }
        if (expectedTargets.size() != chunks.size()) {
            throw new IllegalArgumentException("Duplicate delete chunk target");
        }

        String key = key(operationId, correlationId, mainAppId, fileId);
        PendingDelete pending = new PendingDelete(fileId, operationId, correlationId, mainAppId,
                expectedTargets, completion);
        if (pendingDeletes.putIfAbsent(key, pending) != null) {
            throw new IllegalStateException("Delete operation is already registered");
        }
        System.out.println("[DeleteCoordinator] Delete operation registered: fileId=" + fileId
                + ", operationId=" + operationId + ", correlationId=" + correlationId
                + ", expected=" + expectedTargets.size());
        pending.timeout = timeoutExecutor.schedule(
                () -> finish(key, pending, false, "Timeout waiting for storage confirmations"),
                timeoutSeconds, TimeUnit.SECONDS);
    }

    public void recordDeleteResponse(long fileId, String operationId, String correlationId,
                                     String mainAppId,
                                     String fsId, int chunkOrder, String status) {
        String key = key(operationId, correlationId, mainAppId, fileId);
        PendingDelete pending = pendingDeletes.get(key);
        System.out.println("[DeleteCoordinator] Storage response received: topic="
                + "filemanager/storage/delete-response" + ", fileId=" + fileId
                + ", operationId=" + operationId + ", correlationId=" + correlationId
                + ", fsId=" + fsId + ", chunkOrder=" + chunkOrder + ", status=" + status);
        if (pending == null) {
            System.out.println("[DeleteCoordinator] Ignored response: no matching operation");
            return;
        }
        Target target = new Target(fsId, chunkOrder);
        if (!pending.expectedTargets.contains(target)) {
            System.out.println("[DeleteCoordinator] Ignored response: unexpected storage target");
            return;
        }
        if (!"deleted".equalsIgnoreCase(status) && !"not_found".equalsIgnoreCase(status)) {
            finish(key, pending, false, "Storage deletion failed for " + fsId
                    + " chunk " + chunkOrder + ": " + status);
            return;
        }
        synchronized (pending) {
            if (!pending.responses.add(target)) {
                System.out.println("[DeleteCoordinator] Ignored duplicate response for " + fsId
                        + " chunk " + chunkOrder);
                return;
            }
            System.out.println("[DeleteCoordinator] Successful delete response count: "
                    + pending.responses.size() + "/" + pending.expectedTargets.size());
            if (pending.responses.size() == pending.expectedTargets.size()) {
                finish(key, pending, true, "All chunks deleted");
            }
        }
    }

    public void failDelete(long fileId, String operationId, String correlationId,
                           String mainAppId, String reason) {
        String operationKey = key(operationId, correlationId, mainAppId, fileId);
        PendingDelete pending = pendingDeletes.get(operationKey);
        if (pending == null) {
            return;
        }
        finish(operationKey, pending, false, reason);
    }

    private void finish(String key, PendingDelete pending, boolean success, String reason) {
        if (!pending.finished.compareAndSet(false, true)) {
            return;
        }
        pendingDeletes.remove(key, pending);
        if (pending.timeout != null) {
            pending.timeout.cancel(false);
            System.out.println("[DeleteCoordinator] Timeout cancelled for fileId="
                    + pending.fileId);
        }
        System.out.println("[DeleteCoordinator] Final delete response: status="
                + (success ? "success" : "failed") + ", fileId=" + pending.fileId
                + ", operationId=" + pending.operationId + ", correlationId="
                + pending.correlationId + ", message=" + reason);
        pending.completion.accept(new DeleteResult(pending.fileId, pending.operationId,
                pending.correlationId, success, reason));
    }

    private static String key(String operationId, String correlationId, String mainAppId,
                              long fileId) {
        return operationId + "|" + correlationId + "|" + mainAppId + "|" + fileId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record DeleteResult(long fileId, String operationId, String correlationId,
                               boolean success, String message) {
    }

    private record Target(String fsId, int chunkOrder) {
    }

    private static final class PendingDelete {
        private final long fileId;
        private final String operationId;
        private final String correlationId;
        private final String mainAppId;
        private final Set<Target> expectedTargets;
        private final Set<Target> responses = new HashSet<>();
        private final Consumer<DeleteResult> completion;
        private final java.util.concurrent.atomic.AtomicBoolean finished =
                new java.util.concurrent.atomic.AtomicBoolean();
        private ScheduledFuture<?> timeout;

        private PendingDelete(long fileId, String operationId, String correlationId,
                              String mainAppId,
                              Set<Target> expectedTargets, Consumer<DeleteResult> completion) {
            this.fileId = fileId;
            this.operationId = operationId;
            this.correlationId = correlationId;
            this.mainAppId = mainAppId;
            this.expectedTargets = Set.copyOf(expectedTargets);
            this.completion = completion;
        }
    }
}
