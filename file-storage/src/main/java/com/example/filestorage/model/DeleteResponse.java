package com.example.filestorage.model;

public class DeleteResponse {
    private final String operationId;
    private final String correlationId;
    private final String mainAppId;
    private final long fileId;
    private final int chunkOrder;
    private final String fsId;
    private final String status;

    public DeleteResponse(String operationId, String correlationId, String mainAppId,
                          long fileId, int chunkOrder, String fsId, String status) {
        this.operationId = operationId;
        this.correlationId = correlationId;
        this.mainAppId = mainAppId;
        this.fileId = fileId;
        this.chunkOrder = chunkOrder;
        this.fsId = fsId;
        this.status = status;
    }
}
