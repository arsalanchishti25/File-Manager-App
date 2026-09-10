package com.example.mainapp.mqtt;

public final class ErrorResponse {
    private final String operationId;
    private final String correlationId;
    private final String mainAppId;
    private final String sourceServiceId;
    private final long timestamp;
    private final String errorCode;
    private final String errorMessage;

    public ErrorResponse(String operationId, String correlationId, String mainAppId,
                         String sourceServiceId, String errorCode, String errorMessage) {
        this.operationId = operationId;
        this.correlationId = correlationId;
        this.mainAppId = mainAppId;
        this.sourceServiceId = sourceServiceId;
        this.timestamp = System.currentTimeMillis();
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public String getSourceServiceId() { return sourceServiceId; }
    public long getTimestamp() { return timestamp; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
