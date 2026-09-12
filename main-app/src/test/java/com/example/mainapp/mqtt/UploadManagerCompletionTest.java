package com.example.mainapp.mqtt;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadManagerCompletionTest {

    @Test
    void acceptsValidCompletionFields() throws Exception {
        JsonObject response = JsonParser.parseString("""
                {
                  "fileId": 11,
                  "operationId": "operation-1",
                  "correlationId": "correlation-1",
                  "mainAppId": "main-app-1",
                  "status": "complete",
                  "chunks": []
                }
                """).getAsJsonObject();

        assertEquals(11, UploadManager.requireLong(response, "fileId"));
        assertEquals("complete", UploadManager.requireString(response, "status"));
    }

    @Test
    void rejectsMissingRequiredField() {
        JsonObject response = new JsonObject();

        Exception error = assertThrows(Exception.class,
                () -> UploadManager.requireString(response, "operationId"));

        assertEquals("Aggregator completion response is missing required field: operationId",
                error.getMessage());
    }

    @Test
    void rejectsExplicitJsonNull() {
        JsonObject response = JsonParser.parseString(
                "{\"fsId\":null}").getAsJsonObject();

        Exception error = assertThrows(Exception.class,
                () -> UploadManager.requireString(response, "fsId"));

        assertEquals("Aggregator completion response is missing required field: fsId",
                error.getMessage());
    }

    @Test
    void rejectsMalformedNumericField() {
        JsonObject response = JsonParser.parseString(
                "{\"fileId\":\"not-a-number\"}").getAsJsonObject();

        Exception error = assertThrows(Exception.class,
                () -> UploadManager.requireLong(response, "fileId"));

        assertEquals("Aggregator completion response has invalid field: fileId",
                error.getMessage());
    }

    @Test
    void rejectsMissingChunkArray() {
        JsonObject response = new JsonObject();

        Exception error = assertThrows(Exception.class,
                () -> UploadManager.requireArray(response, "chunks"));

        assertEquals("Aggregator completion response is missing required field: chunks",
                error.getMessage());
    }

    @Test
    void rejectsMalformedJson() {
        Exception error = assertThrows(Exception.class,
        () -> UploadManager.parseCompletionPayload("{not-json"));

        assertEquals("Malformed Aggregator completion response", error.getMessage());
    }

    @Test
    void rejectsAggregatorFailureResponse() {
        JsonObject response = JsonParser.parseString(
                "{\"status\":\"failed\",\"error\":\"storage unavailable\"}").getAsJsonObject();

        Exception error = assertThrows(Exception.class,
                () -> UploadManager.validateCompletionStatus(response,
                        UploadManager.requireString(response, "status")));

        assertEquals("Aggregator upload failed: storage unavailable", error.getMessage());
    }

    @Test
    void acceptsContiguousFourChunkOrders() throws Exception {
        UploadManager.validateChunkMetadata(chunks(1, 2, 3, 4));
    }

    @Test
    void rejectsZeroAndDuplicateOrders() {
        Exception error = assertThrows(Exception.class,
                () -> UploadManager.validateChunkMetadata(chunks(0, 0, 0, 0)));
        assertEquals("Invalid chunkOrder in completion response: 0", error.getMessage());

        error = assertThrows(Exception.class,
                () -> UploadManager.validateChunkMetadata(chunks(1, 1, 2, 3)));
        assertEquals("Duplicate chunkOrder in completion response: 1", error.getMessage());
    }

    @Test
    void rejectsNegativeAndNonContiguousOrders() {
        Exception error = assertThrows(Exception.class,
                () -> UploadManager.validateChunkMetadata(chunks(-1, 1, 2, 3)));
        assertEquals("Invalid chunkOrder in completion response: -1", error.getMessage());

        error = assertThrows(Exception.class,
                () -> UploadManager.validateChunkMetadata(chunks(1, 2, 4, 5)));
        assertEquals("Non-contiguous chunkOrder in completion response; missing: 3",
                error.getMessage());
    }

    @Test
    void rejectsMissingNullAndEmptyChunkFields() {
        JsonArray chunks = chunks(1, 2, 3, 4);
        chunks.get(1).getAsJsonObject().remove("chunkOrder");
        assertThrows(Exception.class, () -> UploadManager.validateChunkMetadata(chunks));

        JsonArray nullFsIdChunks = chunks(1, 2, 3, 4);
        nullFsIdChunks.get(1).getAsJsonObject().add("fsId", com.google.gson.JsonNull.INSTANCE);
        assertThrows(Exception.class, () -> UploadManager.validateChunkMetadata(nullFsIdChunks));

        JsonArray emptyCrcChunks = chunks(1, 2, 3, 4);
        emptyCrcChunks.get(1).getAsJsonObject().addProperty("crc32", "");
        assertThrows(Exception.class, () -> UploadManager.validateChunkMetadata(emptyCrcChunks));
    }

    private static JsonArray chunks(int... orders) {
        JsonArray result = new JsonArray();
        for (int order : orders) {
            JsonObject chunk = new JsonObject();
            chunk.addProperty("chunkOrder", order);
            chunk.addProperty("volumeGroup", order > 0 ? order : 1);
            chunk.addProperty("fsId", "fs-" + Math.max(order, 1));
            chunk.addProperty("crc32", "crc-" + Math.max(order, 1));
            result.add(chunk);
        }
        return result;
    }
}
