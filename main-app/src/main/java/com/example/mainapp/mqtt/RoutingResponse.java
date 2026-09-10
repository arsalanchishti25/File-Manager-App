package com.example.mainapp.mqtt;

public class RoutingResponse {
    private String operation;
    private String operationId;
    private String correlationId;
    private String mainAppId;
    private boolean success;

    public String getOperation() { return operation; }
    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public boolean isSuccess() { return success; }
}
