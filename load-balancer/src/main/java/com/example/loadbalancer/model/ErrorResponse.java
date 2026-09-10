package com.example.loadbalancer.model;

public class ErrorResponse {
    private final String operationId;
    private final String correlationId;
    private final String mainAppId;
    private final String sourceServiceId;
    private final long timestamp = System.currentTimeMillis();
    private final String errorCode;
    private final String errorMessage;

    public ErrorResponse(String operationId, String correlationId, String mainAppId,
                         String sourceServiceId, String errorCode, String errorMessage) {
        this.operationId = operationId;
        this.correlationId = correlationId;
        this.mainAppId = mainAppId;
        this.sourceServiceId = sourceServiceId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
