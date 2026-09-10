package com.example.mainapp.mqtt;

public class DownloadInstruction {
    private String operationId;
    private String correlationId;
    private String mainAppId;
    private String sourceServiceId;
    private long fileId;
    private String filename;

    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public String getSourceServiceId() { return sourceServiceId; }
    public long getFileId() { return fileId; }
    public String getFilename() { return filename; }
}
