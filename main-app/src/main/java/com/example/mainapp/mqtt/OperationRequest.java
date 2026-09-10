package com.example.mainapp.mqtt;

import com.google.gson.JsonObject;

import java.util.UUID;

public final class OperationRequest {
    private final String operationId = UUID.randomUUID().toString();
    private final String correlationId = UUID.randomUUID().toString();
    private final String operation;
    private final String mainAppId;
    private final long fileId;
    private final long userId;

    public OperationRequest(String operation, String mainAppId, long fileId, long userId) {
        if (operation == null || operation.isBlank() || mainAppId == null || mainAppId.isBlank()
                || fileId <= 0) {
            throw new IllegalArgumentException("Invalid operation request");
        }
        this.operation = operation.toUpperCase();
        this.mainAppId = mainAppId;
        this.fileId = fileId;
        this.userId = userId;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("operation", operation);
        json.addProperty("operationId", operationId);
        json.addProperty("correlationId", correlationId);
        json.addProperty("mainAppId", mainAppId);
        json.addProperty("fileId", fileId);
        json.addProperty("userId", userId);
        return json;
    }

    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
}
