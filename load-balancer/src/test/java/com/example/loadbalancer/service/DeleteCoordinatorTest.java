package com.example.loadbalancer.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeleteCoordinatorTest {

    @Test
    void fourDeletedResponsesCompleteOnce() throws Exception {
        AtomicReference<DeleteCoordinator.DeleteResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        DeleteCoordinator coordinator = register(result, done);

        for (int i = 1; i <= 4; i++) {
            coordinator.recordDeleteResponse(15, "op", "corr", "main-app-1",
                    "fs-" + i, i, "deleted");
        }

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(result.get().success());
        assertEquals("All chunks deleted", result.get().message());
    }

    @Test
    void notFoundIsSuccessfulAndDuplicateIsIgnored() throws Exception {
        AtomicReference<DeleteCoordinator.DeleteResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        DeleteCoordinator coordinator = register(result, done);

        coordinator.recordDeleteResponse(15, "op", "corr", "main-app-1",
                "fs-1", 1, "not_found");
        coordinator.recordDeleteResponse(15, "op", "corr", "main-app-1",
                "fs-1", 1, "deleted");
        for (int i = 2; i <= 4; i++) {
            coordinator.recordDeleteResponse(15, "op", "corr", "main-app-1",
                    "fs-" + i, i, "deleted");
        }

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(result.get().success());
    }

    @Test
    void storageFailureCompletesAsFailure() throws Exception {
        AtomicReference<DeleteCoordinator.DeleteResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        DeleteCoordinator coordinator = register(result, done);

        coordinator.recordDeleteResponse(15, "op", "corr", "main-app-1",
                "fs-2", 2, "failed");

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertFalse(result.get().success());
        assertTrue(result.get().message().contains("fs-2"));
    }

    @Test
    void mismatchedIdentityAndTargetAreIgnored() throws Exception {
        AtomicReference<DeleteCoordinator.DeleteResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        DeleteCoordinator coordinator = register(result, done);

        coordinator.recordDeleteResponse(15, "wrong", "corr", "main-app-1",
                "fs-1", 1, "deleted");
        coordinator.recordDeleteResponse(15, "op", "wrong", "main-app-1",
                "fs-1", 1, "deleted");
        coordinator.recordDeleteResponse(15, "op", "corr", "main-app-1",
                "fs-9", 1, "deleted");
        coordinator.recordDeleteResponse(15, "op", "corr", "main-app-1",
                "fs-1", 9, "deleted");

        assertFalse(done.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void immediateResponseAfterRegistrationIsHandled() throws Exception {
        AtomicReference<DeleteCoordinator.DeleteResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        DeleteCoordinator coordinator = register(result, done);
        for (int i = 1; i <= 4; i++) {
            coordinator.recordDeleteResponse(15, "op", "corr", "main-app-1",
                    "fs-" + i, i, "deleted");
        }
        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(result.get().success());
    }

    @Test
    void timeoutCompletesAsFailure() throws Exception {
        AtomicReference<DeleteCoordinator.DeleteResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        DeleteCoordinator coordinator = new DeleteCoordinator();
        coordinator.registerDelete(15, "op-timeout", "corr-timeout", "main-app-1", chunks(),
                1, value -> {
                    result.set(value);
                    done.countDown();
                });

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertFalse(result.get().success());
        assertTrue(result.get().message().contains("Timeout"));
    }

    private DeleteCoordinator register(AtomicReference<DeleteCoordinator.DeleteResult> result,
                                       CountDownLatch done) {
        DeleteCoordinator coordinator = new DeleteCoordinator();
        coordinator.registerDelete(15, "op", "corr", "main-app-1", chunks(), 5, value -> {
            result.set(value);
            done.countDown();
        });
        return coordinator;
    }

    private JsonArray chunks() {
        JsonArray chunks = new JsonArray();
        for (int i = 1; i <= 4; i++) {
            JsonObject chunk = new JsonObject();
            chunk.addProperty("fsId", "fs-" + i);
            chunk.addProperty("chunkOrder", i);
            chunks.add(chunk);
        }
        return chunks;
    }
}
