package com.example.loadbalancer.model;

public class FSContainerInfo {
    private final String id;
    private final String ip;
    private final int port;
    private final int volumeGroup;

    public FSContainerInfo(String id, String ip, int port, int volumeGroup) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.volumeGroup = volumeGroup;
    }

    public String getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public int getVolumeGroup() {
        return volumeGroup;
    }

    @Override
    public String toString() {
        return "FSContainerInfo{" +
                "id='" + id + '\'' +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                ", volumeGroup=" + volumeGroup +
                '}';
    }
}
