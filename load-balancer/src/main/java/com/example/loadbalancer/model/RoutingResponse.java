package com.example.loadbalancer.model;

public final class RoutingResponse {
    private final String operation;
    private final String operationId;
    private final String correlationId;
    private final String mainAppId;
    private final boolean success;

    public RoutingResponse(String operation, String operationId, String correlationId,
                           String mainAppId, boolean success) {
        this.operation = operation;
        this.operationId = operationId;
        this.correlationId = correlationId;
        this.mainAppId = mainAppId;
        this.success = success;
    }

    public String getOperation() { return operation; }
    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public boolean isSuccess() { return success; }
}
