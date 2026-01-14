package com.example.filestorage.mqtt;

import com.example.filestorage.service.StorageService;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.MqttException;
import java.io.IOException;

/**
 * Handles incoming MQTT messages.
 * Processes delete commands from Load Balancer.
 */
public class MqttMessageHandler {

    private final MqttBroker mqttBroker;
    private final StorageService storageService;

    public MqttMessageHandler(MqttBroker mqttBroker, StorageService storageService) {
        this.mqttBroker = mqttBroker;
        this.storageService = storageService;
    }

    /**
     * Start listening to delete commands.
     */
    public void startListening() throws MqttException {
        String containerId = storageService.getConfig().getContainerId();
        String deleteTopic = "fs/delete/" + containerId;

        mqttBroker.subscribe(deleteTopic, (topic, message) -> {
            String payload = new String(message.getPayload());
            System.out.println("\n[MqttMessageHandler] Received delete command: " + payload);
            handleDeleteCommand(payload);
        });
    }

    /**
     * Handle delete command from Load Balancer.
     * Payload format:
     * {
     *   "fileId": 123,
     *   "chunkOrder": 1,
     *   "fsId": "fs-1"
     * }
     */
    private void handleDeleteCommand(String payload) {
        try {
            JsonObject command = mqttBroker.getGson().fromJson(payload, JsonObject.class);
            long fileId = command.get("fileId").getAsLong();
            int chunkOrder = command.has("chunkOrder") ? command.get("chunkOrder").getAsInt() : 0;
            String fsId = command.has("fsId") ? command.get("fsId").getAsString() : "unknown";

            System.out.println("[MqttMessageHandler] Processing delete: fileId=" + fileId + 
                             ", chunkOrder=" + chunkOrder + ", fsId=" + fsId);

            // TODO: Validate that fsId matches this container's ID

            // Delete the chunk
            try {
                storageService.getChunkManager().deleteChunk(fileId, chunkOrder);
                
                // Send confirmation back to Load Balancer
                sendDeleteResponse(fileId, chunkOrder, "deleted");
                
            } catch (IOException e) {
                System.out.println("[MqttMessageHandler] Chunk not found or already deleted: " + e.getMessage());
                // Still send response (not_found is OK)
                sendDeleteResponse(fileId, chunkOrder, "not_found");
            }

        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] Error handling delete command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send delete response to Load Balancer.
     * Topic: fs/delete/response
     */
    private void sendDeleteResponse(long fileId, int chunkOrder, String status) {
        try {
            JsonObject response = new JsonObject();
            response.addProperty("fileId", fileId);
            response.addProperty("chunkOrder", chunkOrder);
            response.addProperty("fsId", storageService.getConfig().getContainerId());
            response.addProperty("status", status);  // "deleted" or "not_found"

            mqttBroker.publish("fs/delete/response", response.toString());
            
            System.out.println("[MqttMessageHandler] Sent delete response: " + status);

        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to send delete response: " + e.getMessage());
        }
    }
}
