package com.example.mainapp.mqtt;

public class DeleteInstruction {
    private String operationId;
    private String correlationId;
    private String mainAppId;
    private long fileId;

    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public long getFileId() { return fileId; }
}
