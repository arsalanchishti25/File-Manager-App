package com.example.mainapp.mqtt;

public class DeleteResponse {
    private String operationId;
    private String correlationId;
    private String mainAppId;
    private long fileId;
    private boolean success;
    private String message;

    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public long getFileId() { return fileId; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
