package com.example.filestorage.model;

public class DeleteInstruction {
    private String operationId;
    private String correlationId;
    private String mainAppId;
    private String fsId;
    private long fileId;
    private int chunkOrder;

    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public String getFsId() { return fsId; }
    public long getFileId() { return fileId; }
    public int getChunkOrder() { return chunkOrder; }
}
