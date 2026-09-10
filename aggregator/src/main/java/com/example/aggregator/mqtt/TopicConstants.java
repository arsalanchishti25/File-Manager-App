package com.example.aggregator.mqtt;

public final class TopicConstants {
    public static final String EVENTS_ERROR = "filemanager/events/error";

    private TopicConstants() {
    }

    public static String upload(String aggregatorId) {
        return "filemanager/aggregator/" + aggregatorId + "/upload";
    }

    public static String download(String aggregatorId) {
        return "filemanager/aggregator/" + aggregatorId + "/download";
    }

    public static String uploadComplete(String mainAppId) {
        return "filemanager/aggregator/upload/complete/" + mainAppId;
    }

    public static String downloadComplete(String mainAppId) {
        return "filemanager/aggregator/download/complete/" + mainAppId;
    }
}
