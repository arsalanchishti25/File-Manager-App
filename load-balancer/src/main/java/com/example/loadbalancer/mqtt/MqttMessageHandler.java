package com.example.loadbalancer.mqtt;

import com.example.loadbalancer.service.RoutingService;
import com.example.loadbalancer.service.DeleteCoordinator;
import com.example.loadbalancer.model.AggregatorInfo;
import com.example.loadbalancer.model.FSContainerInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.eclipse.paho.client.mqttv3.MqttException;
import java.util.List;

/**
 * Handles incoming MQTT messages.
 * Processes upload, download, and delete requests from Main App.
 */
public class MqttMessageHandler {

    private final MqttBroker mqttBroker;
    private final RoutingService routingService;
    private final DeleteCoordinator deleteCoordinator;
    private final TopicPublisher topicPublisher;

    public MqttMessageHandler(MqttBroker mqttBroker, RoutingService routingService) {
        this.mqttBroker = mqttBroker;
        this.routingService = routingService;
        this.deleteCoordinator = new DeleteCoordinator();
        this.topicPublisher = new TopicPublisher(mqttBroker);
    }

    /**
     * Start listening to operations/request topic.
     */
    public void startListening() throws MqttException {
        mqttBroker.subscribe("operations/request", (topic, message) -> {
            String payload = new String(message.getPayload());
            System.out.println("\n[MqttMessageHandler] Received request: " + payload);
            handleOperationRequest(payload);
        });
    }

    /**
     * Handle incoming operation request.
     */
    private void handleOperationRequest(String payload) {
        try {
            JsonObject request = mqttBroker.getGson().fromJson(payload, JsonObject.class);
            String operation = request.get("operation").getAsString();
            String mainAppId = request.has("mainAppId") ? request.get("mainAppId").getAsString() : "unknown";

            System.out.println("[MqttMessageHandler] Processing operation: " + operation + 
                             " from mainAppId: " + mainAppId);

            switch (operation.toUpperCase()) {
                case "UPLOAD":
                    handleUploadRequest(request, mainAppId);
                    break;
                case "DOWNLOAD":
                    handleDownloadRequest(request, mainAppId);
                    break;
                case "DELETE":
                    handleDeleteRequest(request, mainAppId);
                    break;
                default:
                    System.err.println("[MqttMessageHandler] Unknown operation: " + operation);
            }
        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] Error handling request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle UPLOAD request.
     * Route to Aggregator and 4 FS containers.
     */
    private void handleUploadRequest(JsonObject request, String mainAppId) {
        try {
            long fileId = request.get("fileId").getAsLong();
            String filename = request.get("filename").getAsString();
            long fileSize = request.get("fileSize").getAsLong();

            System.out.println("[MqttMessageHandler] UPLOAD request: fileId=" + fileId + 
                             ", filename=" + filename + ", size=" + fileSize);

            // TODO: Implement aggregator selection logic
            AggregatorInfo aggregator = routingService.selectAggregator();

            // TODO: Implement FS container selection logic
            List<FSContainerInfo> fsContainers = routingService.selectFSContainers();

            // Send routing response to Main App
            topicPublisher.publishUploadRoutingResponse(mainAppId, fileId, aggregator, fsContainers);

            System.out.println("[MqttMessageHandler] UPLOAD routing sent for fileId=" + fileId);

        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] UPLOAD request failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle DOWNLOAD request.
     * Route to Aggregator for reassembly.
     */
    private void handleDownloadRequest(JsonObject request, String mainAppId) {
        try {
            long fileId = request.get("fileId").getAsLong();
            JsonArray chunks = request.getAsJsonArray("chunks");

            System.out.println("[MqttMessageHandler] DOWNLOAD request: fileId=" + fileId + 
                             ", chunks=" + chunks.size());

            // TODO: Implement aggregator selection logic
            AggregatorInfo aggregator = routingService.selectAggregator();

            // TODO: Implement FS container selection logic per volume group
            List<FSContainerInfo> fsContainers = routingService.selectFSContainers();

            // Send routing response to Main App
            topicPublisher.publishDownloadRoutingResponse(mainAppId, fileId, aggregator, fsContainers);

            System.out.println("[MqttMessageHandler] DOWNLOAD routing sent for fileId=" + fileId);

        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] DOWNLOAD request failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle DELETE request.
     * Coordinate deletion across FS containers.
     */
    private void handleDeleteRequest(JsonObject request, String mainAppId) {
        try {
            long fileId = request.get("fileId").getAsLong();
            JsonArray chunks = request.getAsJsonArray("chunks");

            System.out.println("[MqttMessageHandler] DELETE request: fileId=" + fileId + 
                             ", chunks=" + chunks.size());

            // Initiate delete coordination
            int totalFSContainers = 0;
            for (int i = 1; i <= 4; i++) {
                totalFSContainers += routingService.getFSContainersInVolumeGroup(i).size();
            }

            deleteCoordinator.initiateDelete(fileId, totalFSContainers);

            // Send delete commands to all FS containers
            topicPublisher.publishDeleteCommands(fileId, chunks);

            // Wait for all confirmations (timeout: 30 seconds)
            boolean completed = deleteCoordinator.waitForDeleteCompletion(fileId, 30);

            if (completed) {
                topicPublisher.publishDeleteResponse(mainAppId, fileId, true, "All chunks deleted");
                System.out.println("[MqttMessageHandler] DELETE completed for fileId=" + fileId);
            } else {
                topicPublisher.publishDeleteResponse(mainAppId, fileId, false, "Timeout waiting for FS confirmations");
                System.err.println("[MqttMessageHandler] DELETE timeout for fileId=" + fileId);
            }

        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] DELETE request failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle delete response from FS containers.
     * Called when FS sends confirmation on fs/delete/response topic.
     */
    public void handleDeleteResponse(String payload) {
        try {
            JsonObject response = mqttBroker.getGson().fromJson(payload, JsonObject.class);
            long fileId = response.get("fileId").getAsLong();
            String fsId = response.get("fsId").getAsString();
            String status = response.get("status").getAsString();

            System.out.println("[MqttMessageHandler] Delete response from " + fsId + 
                             ": fileId=" + fileId + ", status=" + status);

            deleteCoordinator.recordDeleteResponse(fileId, fsId, status);

        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] Error handling delete response: " + e.getMessage());
        }
    }

    public DeleteCoordinator getDeleteCoordinator() {
        return deleteCoordinator;
    }
}
