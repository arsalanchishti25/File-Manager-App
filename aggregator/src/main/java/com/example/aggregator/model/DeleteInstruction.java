package com.example.aggregator.model;

public class DeleteInstruction {
    private String operationId;
    private String correlationId;
    private String mainAppId;
    private String aggregatorId;
    private long fileId;
    private int chunkOrder;
    private String storageContainerId;

    public String getOperationId() { return operationId; }
    public String getCorrelationId() { return correlationId; }
    public String getMainAppId() { return mainAppId; }
    public String getAggregatorId() { return aggregatorId; }
    public long getFileId() { return fileId; }
    public int getChunkOrder() { return chunkOrder; }
    public String getStorageContainerId() { return storageContainerId; }
}
