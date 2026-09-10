package com.example.filestorage.mqtt;

public final class TopicConstants {
    public static final String DELETE_RESPONSE = "filemanager/storage/delete-response";

    private TopicConstants() {
    }

    public static String delete(String containerId) {
        return "filemanager/storage/" + containerId + "/delete";
    }
}
