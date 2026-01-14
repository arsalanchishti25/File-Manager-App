package com.example.aggregator.model;

/**
 * Configuration for Aggregator Container.
 */
public class AggregatorConfig {
    private final String aggregatorId;
    private final int sftpPort;
    private final String workingDirectory;
    private final String brokerUrl;

    public AggregatorConfig(String aggregatorId, int sftpPort, 
                           String workingDirectory, String brokerUrl) {
        this.aggregatorId = aggregatorId;
        this.sftpPort = sftpPort;
        this.workingDirectory = workingDirectory;
        this.brokerUrl = brokerUrl;
    }

    public String getAggregatorId() {
        return aggregatorId;
    }

    public int getSftpPort() {
        return sftpPort;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    @Override
    public String toString() {
        return "AggregatorConfig{" +
                "aggregatorId='" + aggregatorId + '\'' +
                ", sftpPort=" + sftpPort +
                ", workingDirectory='" + workingDirectory + '\'' +
                ", brokerUrl='" + brokerUrl + '\'' +
                '}';
    }
}
