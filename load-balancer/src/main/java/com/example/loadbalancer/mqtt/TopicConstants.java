package com.example.loadbalancer.mqtt;

public final class TopicConstants {
    public static final String OPERATIONS_REQUEST = "filemanager/operations/request";
    public static final String LEGACY_OPERATIONS_REQUEST = "operations/request";
    public static final String STORAGE_DELETE_RESPONSE = "filemanager/storage/delete-response";
    public static final String LEGACY_STORAGE_DELETE_RESPONSE = "fs/delete/response";
    public static final String ERROR_EVENTS = "filemanager/events/error";

    private TopicConstants() {
    }

    public static String operationsResponse(String mainAppId) {
        return "filemanager/operations/response/" + mainAppId;
    }

    public static String routing(String operation, String mainAppId) {
        return "filemanager/routing/" + operation.toLowerCase() + "/" + mainAppId;
    }

    public static String aggregator(String aggregatorId, String operation) {
        return "filemanager/aggregator/" + aggregatorId + "/" + operation.toLowerCase();
    }

    public static String storageDelete(String fsId) {
        return "filemanager/storage/" + fsId + "/delete";
    }
}
