package com.example.loadbalancer.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class OperationRequest {
    private final String operation;
    private final String operationId;
    private final String correlationId;
    private final String mainAppId;
    private final long fileId;
    private final String filename;
    private final long fileSize;
    private final JsonElement chunks;

    private OperationRequest(String operation, String operationId, String correlationId,
                             String mainAppId, long fileId, String filename, long fileSize,
                             JsonElement chunks) {
        this.operation = operation;
        this.operationId = operationId;
        this.correlationId = correlationId;
        this.mainAppId = mainAppId;
        this.fileId = fileId;
        this.filename = filename;
        this.fileSize = fileSize;
        this.chunks = chunks;
    }

    public static OperationRequest parse(JsonObject json) {
        requireString(json, "operation");
        requireString(json, "operationId");
        requireString(json, "correlationId");
        requireString(json, "mainAppId");
        String operation = json.get("operation").getAsString().trim().toUpperCase();
        if (!operation.equals("UPLOAD") && !operation.equals("DOWNLOAD") && !operation.equals("DELETE")) {
            throw new IllegalArgumentException("INVALID_OPERATION");
        }
        long fileId = requiredLong(json, "fileId");
        if (fileId <= 0) {
            throw new IllegalArgumentException("INVALID_FILE_ID");
        }
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;
        long fileSize = json.has("fileSize") ? json.get("fileSize").getAsLong() : 0;
        if ((operation.equals("UPLOAD") || operation.equals("DOWNLOAD"))
                && (!json.has("filename") || json.get("filename").isJsonNull()
                || json.get("filename").getAsString().isBlank())) {
            throw new IllegalArgumentException("MISSING_FIELD: filename");
        }
        JsonElement chunks = json.get("chunks");
        if ((operation.equals("DOWNLOAD") || operation.equals("DELETE"))
                && (chunks == null || !chunks.isJsonArray() || chunks.getAsJsonArray().isEmpty())) {
            throw new IllegalArgumentException("MISSING_FIELD: chunks");
        }
        return new OperationRequest(operation, json.get("operationId").getAsString(),
                json.get("correlationId").getAsString(),
                json.get("mainAppId").getAsString(), fileId, filename, fileSize, chunks);
    }

    private static void requireString(JsonObject json, String name) {
        if (!json.has(name) || json.get(name).isJsonNull()
                || json.get(name).getAsString().isBlank()) {
            throw new IllegalArgumentException("MISSING_FIELD: " + name);
        }
    }

    private static long requiredLong(JsonObject json, String name) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException("MISSING_FIELD: " + name);
        }
        return json.get(name).getAsLong();
    }

    public String getOperation() { return operation; }
    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public long getFileId() { return fileId; }
    public String getFilename() { return filename; }
    public long getFileSize() { return fileSize; }
    public JsonElement getChunks() { return chunks; }
}
