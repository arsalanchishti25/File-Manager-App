package com.example.aggregator.model;

import java.util.List;

/**
 * Instructions from Main App for uploading file chunks.
 */
public class UploadInstructions {
    private long fileId;
    private String filename;
    private long fileSize;
    private String mainAppId;  // Added: identifies which Main App instance initiated this upload
    private long userId;
    private String operationId;
    private String correlationId;
    private String sourceServiceId;
    private boolean updateExisting;
    private String stagingToken;
    private List<FSTarget> fsContainers;

    public static class FSTarget {
        private int chunkOrder;
        private String fsId;
        private String ip;
        private int port;
        private int volumeGroup;

        public int getChunkOrder() {
            return chunkOrder;
        }

        public void setChunkOrder(int chunkOrder) {
            this.chunkOrder = chunkOrder;
        }

        public String getFsId() {
            return fsId;
        }

        public void setFsId(String fsId) {
            this.fsId = fsId;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getVolumeGroup() {
            return volumeGroup;
        }

        public void setVolumeGroup(int volumeGroup) {
            this.volumeGroup = volumeGroup;
        }

        @Override
        public String toString() {
            return "FSTarget{" +
                    "chunkOrder=" + chunkOrder +
                    ", fsId='" + fsId + '\'' +
                    ", ip='" + ip + '\'' +
                    ", port=" + port +
                    ", volumeGroup=" + volumeGroup +
                    '}';
        }
    }

    public long getFileId() {
        return fileId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMainAppId() {
        return mainAppId;
    }

    public void setMainAppId(String mainAppId) {
        this.mainAppId = mainAppId;
    }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getSourceServiceId() { return sourceServiceId; }
    public void setSourceServiceId(String sourceServiceId) { this.sourceServiceId = sourceServiceId; }
    public boolean isUpdateExisting() { return updateExisting; }
    public void setUpdateExisting(boolean updateExisting) { this.updateExisting = updateExisting; }
    public String getStagingToken() { return stagingToken; }
    public void setStagingToken(String stagingToken) { this.stagingToken = stagingToken; }

    public List<FSTarget> getFsContainers() {
        return fsContainers;
    }

    public void setFsContainers(List<FSTarget> fsContainers) {
        this.fsContainers = fsContainers;
    }

    @Override
    public String toString() {
        return "UploadInstructions{" +
                "fileId=" + fileId +
                ", filename='" + filename + '\'' +
                ", fileSize=" + fileSize +
                ", mainAppId='" + mainAppId + '\'' +
                ", fsContainers=" + fsContainers +
                '}';
    }
}
