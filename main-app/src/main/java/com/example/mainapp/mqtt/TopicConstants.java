package com.example.mainapp.mqtt;

public final class TopicConstants {
    public static final String OPERATIONS_REQUEST = "filemanager/operations/request";

    private TopicConstants() {
    }

    public static String operationsResponse(String mainAppId) {
        return "filemanager/operations/response/" + mainAppId;
    }

    public static String aggregator(String aggregatorId, String operation) {
        return "filemanager/aggregator/" + aggregatorId + "/" + operation.toLowerCase();
    }

    public static String uploadComplete(String mainAppId) {
        return "filemanager/aggregator/upload/complete/" + mainAppId;
    }

    public static String downloadComplete(String mainAppId) {
        return "filemanager/aggregator/download/complete/" + mainAppId;
    }
}
