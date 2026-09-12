package com.example.loadbalancer.mqtt;

import com.example.loadbalancer.service.RoutingService;
import com.example.loadbalancer.service.DeleteCoordinator;
import com.example.loadbalancer.service.HealthMonitor;
import com.example.loadbalancer.model.AggregatorInfo;
import com.example.loadbalancer.model.FSContainerInfo;
import com.example.loadbalancer.model.OperationRequest;
import com.example.loadbalancer.model.ErrorResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
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
    private final HealthMonitor healthMonitor;

    public MqttMessageHandler(MqttBroker mqttBroker, RoutingService routingService, HealthMonitor healthMonitor) {
        this.mqttBroker = mqttBroker;
        this.routingService = routingService;
        this.healthMonitor = healthMonitor;
        this.deleteCoordinator = new DeleteCoordinator();
        this.topicPublisher = new TopicPublisher(mqttBroker);
    }

    /**
     * Start listening to operations/request topic.
     */
    public void startListening() throws MqttException {
        IMqttMessageListener listener = (topic, message) -> {
            String payload = new String(message.getPayload());
            System.out.println("\n[MqttMessageHandler] Received request: " + payload);
            handleOperationRequest(payload);
        };
        mqttBroker.subscribe(TopicConstants.OPERATIONS_REQUEST, listener);
        mqttBroker.subscribe(TopicConstants.LEGACY_OPERATIONS_REQUEST, listener);
    }

    /**
     * Handle incoming operation request.
     */
    private void handleOperationRequest(String payload) {
        JsonObject request = null;
        try {
            request = mqttBroker.getGson().fromJson(payload, JsonObject.class);
            OperationRequest parsed = OperationRequest.parse(request);
            String operation = parsed.getOperation();
            String mainAppId = parsed.getMainAppId();

            System.out.println("[MqttMessageHandler] Processing operation: " + operation + 
                             " from mainAppId: " + mainAppId);

            switch (operation.toUpperCase()) {
                case "UPLOAD":
                    handleUploadRequest(request, parsed);
                    break;
                case "DOWNLOAD":
                    handleDownloadRequest(request, parsed);
                    break;
                case "DELETE":
                    handleDeleteRequest(request, parsed);
                    break;
                default:
                    System.err.println("[MqttMessageHandler] Unknown operation: " + operation);
            }
        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] Error handling request: " + e.getMessage());
            publishRequestError(request, e);
        }
    }

    private void publishRequestError(JsonObject request, Exception exception) {
        String operationId = stringValue(request, "operationId");
        String correlationId = stringValue(request, "correlationId");
        String mainAppId = stringValue(request, "mainAppId");
        long fileId = longValue(request, "fileId");
        String message = exception.getMessage() == null
                ? "Invalid operation request" : exception.getMessage();
        String code = message.startsWith("MISSING_FIELD:")
                ? "MISSING_FIELD" : "INVALID_REQUEST";

        publishError(operationId, correlationId, mainAppId, fileId, code, message);
    }

    private String stringValue(JsonObject request, String name) {
        if (request == null || !request.has(name) || request.get(name).isJsonNull()) {
            return null;
        }
        return request.get(name).getAsString();
    }

    private long longValue(JsonObject request, String name) {
        if (request == null || !request.has(name) || request.get(name).isJsonNull()) {
            return 0;
        }
        return request.get(name).getAsLong();
    }

    private void publishError(String operationId, String correlationId, String mainAppId,
                              String code, String message) {
        publishError(operationId, correlationId, mainAppId, 0, code, message);
    }

    private void publishError(String operationId, String correlationId, String mainAppId,
                              long fileId, String code, String message) {
        try {
            String payload = mqttBroker.getGson().toJson(new ErrorResponse(operationId, correlationId,
                    mainAppId, "load-balancer", code, message));
            mqttBroker.publish(TopicConstants.ERROR_EVENTS, payload);

            if (mainAppId != null && !mainAppId.isBlank()) {
                JsonObject response = new JsonObject();
                response.addProperty("success", false);
                response.addProperty("status", "failed");
                response.addProperty("operationId", operationId);
                response.addProperty("correlationId", correlationId);
                response.addProperty("mainAppId", mainAppId);
                if (fileId > 0) {
                    response.addProperty("fileId", fileId);
                }
                response.addProperty("errorCode", code);
                response.addProperty("message", message);
                mqttBroker.publish(TopicConstants.operationsResponse(mainAppId), response.toString());
            }
        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to publish error: " + e.getMessage());
        }
    }

    /**
     * Handle UPLOAD request.
     * Route to Aggregator and 4 FS containers.
     */
    private void handleUploadRequest(JsonObject request, OperationRequest parsed) {
        try {
            long fileId = parsed.getFileId();
            String filename = parsed.getFilename();
            long fileSize = parsed.getFileSize();

            System.out.println("[MqttMessageHandler] UPLOAD request: fileId=" + fileId + 
                             ", filename=" + filename + ", size=" + fileSize);

            // TODO: Implement aggregator selection logic
            AggregatorInfo aggregator = routingService.selectAggregator();

            // Select healthy FS containers (one per volume group)
            List<FSContainerInfo> fsContainers = routingService.selectFSContainers(healthMonitor);

            // Send routing response to Main App
            topicPublisher.publishUploadRoutingResponse(parsed.getMainAppId(), parsed.getOperationId(),
                    parsed.getCorrelationId(), fileId, aggregator, fsContainers);

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
    private void handleDownloadRequest(JsonObject request, OperationRequest parsed) {
        try {
            long fileId = parsed.getFileId();
            JsonArray chunks = request.getAsJsonArray("chunks");

            System.out.println("[MqttMessageHandler] DOWNLOAD request: fileId=" + fileId + 
                             ", chunks=" + chunks.size());

            // TODO: Implement aggregator selection logic
            AggregatorInfo aggregator = routingService.selectAggregator();

            // Select healthy FS containers (one per volume group)
            List<FSContainerInfo> fsContainers = routingService.selectFSContainers(healthMonitor);

            // Send routing response to Main App
            topicPublisher.publishDownloadRoutingResponse(parsed.getMainAppId(), parsed.getOperationId(),
                    parsed.getCorrelationId(), fileId, aggregator, fsContainers);

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
    private void handleDeleteRequest(JsonObject request, OperationRequest parsed) {
        try {
            long fileId = parsed.getFileId();
            String mainAppId = parsed.getMainAppId();
            JsonArray chunks = request.getAsJsonArray("chunks");

            System.out.println("[MqttMessageHandler] DELETE request: fileId=" + fileId + 
                             ", chunks=" + chunks.size());

            deleteCoordinator.registerDelete(fileId, parsed.getOperationId(),
                    parsed.getCorrelationId(), mainAppId, chunks, 30,
                    result -> publishDeleteResult(mainAppId, result));

            // Send delete commands to all FS containers
            topicPublisher.publishDeleteCommands(fileId, parsed.getOperationId(),
                    parsed.getCorrelationId(), mainAppId, chunks);
            System.out.println("[MqttMessageHandler] DELETE commands dispatched asynchronously for fileId="
                    + fileId);

        } catch (Exception e) {
            if (requestHasDeleteIdentity(parsed)) {
                deleteCoordinator.failDelete(parsed.getFileId(), parsed.getOperationId(),
                        parsed.getCorrelationId(), parsed.getMainAppId(),
                        "Failed to dispatch storage delete commands: "
                                + e.getMessage());
            }
            System.err.println("[MqttMessageHandler] DELETE request failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean requestHasDeleteIdentity(OperationRequest parsed) {
        return parsed != null && parsed.getFileId() > 0
                && parsed.getOperationId() != null && parsed.getCorrelationId() != null;
    }

    private void publishDeleteResult(String mainAppId, DeleteCoordinator.DeleteResult result) {
        try {
            topicPublisher.publishDeleteResponse(mainAppId, result.operationId(),
                    result.correlationId(), result.fileId(), result.success(), result.message());
            System.out.println("[MqttMessageHandler] Final delete response published: topic="
                    + TopicConstants.operationsResponse(mainAppId) + ", status="
                    + (result.success() ? "success" : "failed") + ", fileId="
                    + result.fileId());
        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to publish final delete response: "
                    + e.getMessage());
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
            String operationId = response.get("operationId").getAsString();
            String correlationId = response.get("correlationId").getAsString();
            String mainAppId = response.get("mainAppId").getAsString();
            String fsId = response.get("fsId").getAsString();
            int chunkOrder = response.get("chunkOrder").getAsInt();
            String status = response.get("status").getAsString();

            deleteCoordinator.recordDeleteResponse(fileId, operationId, correlationId,
                    mainAppId, fsId, chunkOrder, status);

        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] Error handling delete response: " + e.getMessage());
        }
    }

    public DeleteCoordinator getDeleteCoordinator() {
        return deleteCoordinator;
    }
}
