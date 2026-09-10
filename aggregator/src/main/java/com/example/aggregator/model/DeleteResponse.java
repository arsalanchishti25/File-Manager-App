package com.example.aggregator.model;

public class DeleteResponse {
    private String operationId;
    private String correlationId;
    private String mainAppId;
    private String storageContainerId;
    private long fileId;
    private String status;

    public DeleteResponse(String operationId, String correlationId, String mainAppId,
                          String storageContainerId, long fileId, String status) {
        this.operationId = operationId;
        this.correlationId = correlationId;
        this.mainAppId = mainAppId;
        this.storageContainerId = storageContainerId;
        this.fileId = fileId;
        this.status = status;
    }
}
