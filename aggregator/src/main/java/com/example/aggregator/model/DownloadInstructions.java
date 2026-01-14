package com.example.aggregator.model;

import java.util.List;

/**
 * Instructions from Main App for downloading and reassembling file chunks.
 */
public class DownloadInstructions {
    private long fileId;
    private String filename;
    private List<ChunkLocation> chunks;

    public static class ChunkLocation {
        private int chunkOrder;
        private String fsId;
        private String ip;
        private int port;
        private String crc32;

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

        public String getCrc32() {
            return crc32;
        }

        public void setCrc32(String crc32) {
            this.crc32 = crc32;
        }

        @Override
        public String toString() {
            return "ChunkLocation{" +
                    "chunkOrder=" + chunkOrder +
                    ", fsId='" + fsId + '\'' +
                    ", ip='" + ip + '\'' +
                    ", port=" + port +
                    ", crc32='" + crc32 + '\'' +
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

    public List<ChunkLocation> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkLocation> chunks) {
        this.chunks = chunks;
    }

    @Override
    public String toString() {
        return "DownloadInstructions{" +
                "fileId=" + fileId +
                ", filename='" + filename + '\'' +
                ", chunks=" + chunks +
                '}';
    }
}
