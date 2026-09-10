package com.example.filestorage.mqtt;

import com.example.filestorage.service.StorageService;
import com.example.filestorage.model.DeleteInstruction;
import com.example.filestorage.model.DeleteResponse;
import com.example.filestorage.model.ErrorResponse;
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
        String deleteTopic = TopicConstants.delete(containerId);

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
            DeleteInstruction command = mqttBroker.getGson().fromJson(payload, DeleteInstruction.class);
            if (command == null || command.getOperationId() == null || command.getOperationId().isBlank()
                    || command.getFileId() <= 0 || command.getChunkOrder() <= 0
                    || command.getFsId() == null || command.getFsId().isBlank()) {
                throw new IllegalArgumentException("Missing required delete field");
            }
            long fileId = command.getFileId();
            int chunkOrder = command.getChunkOrder();
            String fsId = command.getFsId();

            System.out.println("[MqttMessageHandler] Processing delete: fileId=" + fileId + 
                             ", chunkOrder=" + chunkOrder + ", fsId=" + fsId);

            // TODO: Validate that fsId matches this container's ID

            // Delete the chunk
            try {
                storageService.getChunkManager().deleteChunk(fileId, chunkOrder);
                
                // Send confirmation back to Load Balancer
                sendDeleteResponse(command, "deleted");
                
            } catch (IOException e) {
                System.out.println("[MqttMessageHandler] Chunk not found or already deleted: " + e.getMessage());
                // Still send response (not_found is OK)
                sendDeleteResponse(command, "not_found");
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
    private void sendDeleteResponse(DeleteInstruction command, String status) {
        try {
            DeleteResponse response = new DeleteResponse(command.getOperationId(),
                    command.getCorrelationId(), command.getMainAppId(), command.getFileId(),
                    command.getChunkOrder(), storageService.getConfig().getContainerId(), status);

            mqttBroker.publish(TopicConstants.DELETE_RESPONSE,
                    mqttBroker.getGson().toJson(response));
            
            System.out.println("[MqttMessageHandler] Sent delete response: " + status);

        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to send delete response: " + e.getMessage());
        }
    }
}
