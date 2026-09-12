package com.example.aggregator.mqtt;

import com.example.aggregator.config.AggregatorAppConfig;
import com.example.aggregator.model.AggregatorConfig;
import com.example.aggregator.model.UploadInstructions;
import com.example.aggregator.model.DownloadInstructions;
import com.example.aggregator.model.ErrorResponse;
import com.example.aggregator.service.UploadHandler;
import com.example.aggregator.service.DownloadHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.MqttException;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Monitors SFTP working directory for incoming files and instructions.
 * Handles upload and download operations.
 */
public class MqttMessageHandler {

    private final MqttBroker mqttBroker;
    private final AggregatorConfig config;
    private final UploadHandler uploadHandler;
    private final DownloadHandler downloadHandler;
    private volatile boolean running = false;
    private final ExecutorService operationExecutor = Executors.newFixedThreadPool(4);

    public MqttMessageHandler(MqttBroker mqttBroker, AggregatorAppConfig appConfig) {
        this.mqttBroker = mqttBroker;
        this.config = appConfig.toModel();
        this.uploadHandler = new UploadHandler(config.getWorkingDirectory(), appConfig);
        this.downloadHandler = new DownloadHandler(config.getWorkingDirectory(), appConfig);
    }

    /**
     * Start monitoring working directory for new files.
     */
    public void startMonitoring() {
        running = true;
        try {
            mqttBroker.subscribe(TopicConstants.upload(config.getAggregatorId()),
                    (topic, message) -> dispatchUpload(new String(message.getPayload())));
            mqttBroker.subscribe(TopicConstants.download(config.getAggregatorId()),
                    (topic, message) -> dispatchDownload(new String(message.getPayload())));
        } catch (MqttException e) {
            throw new IllegalStateException("Unable to subscribe to direct aggregator topics", e);
        }
        
        Thread monitorThread = new Thread(() -> {
            System.out.println("[MqttMessageHandler] Started monitoring: " + config.getWorkingDirectory());
            
            while (running) {
                try {
                    // Check for retrieval_instructions.json files
                    Path workingPath = Paths.get(config.getWorkingDirectory());
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(workingPath, "retrieval_instructions*.json")) {
                        for (Path instructionsFile : stream) {
                            System.out.println("\n[MqttMessageHandler] Found retrieval instructions file: " + 
                                             instructionsFile.getFileName());
                            processRetrievalInstructions(instructionsFile.toFile());
                        }
                    }
                    
                    Thread.sleep(2000);  // Check every 2 seconds
                    
                } catch (Exception e) {
                    System.err.println("[MqttMessageHandler] Monitoring error: " + e.getMessage());
                }
            }
        });
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * Stop monitoring.
     */
    public void stopMonitoring() {
        running = false;
        operationExecutor.shutdown();
    }

    private void dispatchUpload(String payload) {
        try {
            UploadInstructions instructions =
                    mqttBroker.getGson().fromJson(payload, UploadInstructions.class);
            validateUpload(instructions);
            operationExecutor.submit(() -> processUploadInstruction(instructions));
        } catch (RejectedExecutionException e) {
            publishError(null, null, null, "EXECUTOR_REJECTED", "Upload executor is shutting down");
        } catch (Exception e) {
            publishError(null, null, null, "INVALID_JSON", "Invalid upload instruction");
        }
    }

    private void dispatchDownload(String payload) {
        try {
            DownloadInstructions instructions =
                    mqttBroker.getGson().fromJson(payload, DownloadInstructions.class);
            validateDownload(instructions);
            operationExecutor.submit(() -> processDownloadInstruction(instructions));
        } catch (RejectedExecutionException e) {
            publishError(null, null, null, "EXECUTOR_REJECTED", "Download executor is shutting down");
        } catch (Exception e) {
            publishError(null, null, null, "INVALID_JSON", "Invalid download instruction");
        }
    }

    private void validateUpload(UploadInstructions instructions) {
        if (instructions == null || instructions.getOperationId() == null
                || instructions.getOperationId().isBlank()
                || instructions.getMainAppId() == null || instructions.getMainAppId().isBlank()
                || instructions.getUserId() <= 0 || instructions.getFileId() <= 0 || instructions.getFilename() == null
                || instructions.getFilename().isBlank() || instructions.getFileSize() < 0
                || instructions.getFsContainers() == null || instructions.getFsContainers().size() != 4) {
            throw new IllegalArgumentException("INVALID_TARGET");
        }
    }

    private void validateDownload(DownloadInstructions instructions) {
        if (instructions == null || instructions.getOperationId() == null
                || instructions.getOperationId().isBlank()
                || instructions.getMainAppId() == null || instructions.getMainAppId().isBlank()
                || instructions.getFileId() <= 0 || instructions.getChunks() == null
                || instructions.getChunks().size() != 4) {
            throw new IllegalArgumentException("INVALID_TARGET");
        }
    }

    private void processUploadInstruction(UploadInstructions instructions) {
        try {
            File uploadedFile = new File(config.getWorkingDirectory(),
                    "fileId_" + instructions.getFileId() + ".bin");
            if (!uploadedFile.exists()) {
                throw new IllegalStateException("Uploaded file is not available");
            }
            sendUploadCompleteNotification(instructions,
                    uploadHandler.processUpload(uploadedFile, instructions));
        } catch (Exception e) {
            publishUploadFailure(instructions, e.getMessage());
            publishError(instructions.getOperationId(), instructions.getCorrelationId(),
                    instructions.getMainAppId(), "PROCESSING_FAILED", "Upload processing failed");
        }
    }

    private void processDownloadInstruction(DownloadInstructions instructions) {
        try {
            File result = downloadHandler.processDownload(instructions);
            sendDownloadCompleteNotification(instructions, result);
        } catch (Exception e) {
            publishError(instructions.getOperationId(), instructions.getCorrelationId(),
                    instructions.getMainAppId(), "PROCESSING_FAILED", "Download processing failed");
        }
    }

    private void publishError(String operationId, String correlationId, String mainAppId,
                              String code, String message) {
        try {
            mqttBroker.publish(TopicConstants.EVENTS_ERROR,
                    mqttBroker.getGson().toJson(new ErrorResponse(operationId, correlationId,
                            mainAppId, config.getAggregatorId(), code, message)));
        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to publish structured error: " + e.getMessage());
        }
    }

    /**
     * Process upload instructions.
     */
    private void processInstructions(File instructionsFile) {
        try {
            String json = new String(Files.readAllBytes(instructionsFile.toPath()));
            UploadInstructions instructions = mqttBroker.getGson().fromJson(json, UploadInstructions.class);

            System.out.println("[MqttMessageHandler] Processing upload for fileId: " + instructions.getFileId());

            // Find the uploaded file
            String filePattern = "fileId_" + instructions.getFileId() + ".bin";
            File uploadedFile = new File(config.getWorkingDirectory(), filePattern);

            if (!uploadedFile.exists()) {
                System.err.println("[MqttMessageHandler] File not found: " + filePattern);
                return;
            }

            // Process upload
            List<String> checksums = uploadHandler.processUpload(uploadedFile, instructions);

            // Send completion notification to Main App
            sendUploadCompleteNotification(instructions, checksums);

            // Delete instructions file
            instructionsFile.delete();

        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] Failed to process upload instructions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process download/retrieval instructions.
     */
    private void processRetrievalInstructions(File instructionsFile) {
        try {
            String json = new String(Files.readAllBytes(instructionsFile.toPath()));
            DownloadInstructions instructions = mqttBroker.getGson().fromJson(json, DownloadInstructions.class);

            System.out.println("[MqttMessageHandler] Processing download for fileId: " + instructions.getFileId());

            // Process download
            File reassembledFile = downloadHandler.processDownload(instructions);

            // Send completion notification to Main App
            sendDownloadCompleteNotification(instructions, reassembledFile);

            // Delete instructions file
            instructionsFile.delete();

        } catch (Exception e) {
            System.err.println("[MqttMessageHandler] Failed to process retrieval instructions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send upload complete notification to Main App.
     * Topic: aggregator/upload/complete/{mainAppId}
     *
     * mainAppId is read from the instructions file written by UploadManager.
     */
    private void sendUploadCompleteNotification(UploadInstructions instructions, List<String> checksums) {
        try {
            // Read mainAppId from instructions — populated by UploadManager before SFTP transfer
            String mainAppId = instructions.getMainAppId();
            if (mainAppId == null || mainAppId.isBlank()) {
                throw new IllegalArgumentException("Missing required mainAppId in upload instructions");
            }

            // Build chunk metadata array so UploadManager can persist it
            JsonArray chunksArray = buildCompletionChunks(instructions.getFsContainers(), checksums);

            JsonObject notification = new JsonObject();
            notification.addProperty("fileId", instructions.getFileId());
            notification.addProperty("operationId", instructions.getOperationId());
            notification.addProperty("correlationId", instructions.getCorrelationId());
            notification.addProperty("mainAppId", mainAppId);
            notification.addProperty("userId", instructions.getUserId());
            notification.addProperty("filename", instructions.getFilename());
            notification.addProperty("status", "complete");
            notification.addProperty("aggregatorId", config.getAggregatorId());
            notification.add("chunks", chunksArray);

            String topic = TopicConstants.uploadComplete(mainAppId);
            mqttBroker.publish(topic, notification.toString());

            System.out.println("[MqttMessageHandler] Sent upload complete notification to topic: " + topic +
                               " for fileId: " + instructions.getFileId());

        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to send upload notification: " + e.getMessage());
        }

    }

    static JsonArray buildCompletionChunks(List<UploadInstructions.FSTarget> fsTargets,
                                            List<String> checksums) {
        JsonArray chunksArray = new JsonArray();
        for (int i = 0; i < fsTargets.size() && i < checksums.size(); i++) {
            UploadInstructions.FSTarget target = fsTargets.get(i);
            JsonObject chunkJson = new JsonObject();
            chunkJson.addProperty("chunkOrder", i + 1);
            chunkJson.addProperty("volumeGroup", target.getVolumeGroup());
            chunkJson.addProperty("fsId", target.getFsId());
            chunkJson.addProperty("crc32", checksums.get(i));
            chunksArray.add(chunkJson);
        }
        return chunksArray;
    }

    private void publishUploadFailure(UploadInstructions instructions, String message) {
        try {
            JsonObject notification = new JsonObject();
            notification.addProperty("fileId", instructions.getFileId());
            notification.addProperty("operationId", instructions.getOperationId());
            notification.addProperty("correlationId", instructions.getCorrelationId());
            notification.addProperty("mainAppId", instructions.getMainAppId());
            notification.addProperty("userId", instructions.getUserId());
            notification.addProperty("filename", instructions.getFilename());
            notification.addProperty("status", "failed");
            notification.addProperty("error", message == null ? "Upload processing failed" : message);
            mqttBroker.publish(TopicConstants.uploadComplete(instructions.getMainAppId()),
                    notification.toString());
        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to send upload failure notification: "
                    + e.getMessage());
        }
    }

    /**
     * Send download complete notification to Main App.
     * Topic: aggregator/download/complete/{mainAppId}
     *
     * mainAppId is read from the retrieval_instructions file written by DownloadManager.
     */
    private void sendDownloadCompleteNotification(DownloadInstructions instructions, File reassembledFile) {
        try {
            // Read mainAppId from instructions — populated by DownloadManager before SFTP transfer
            String mainAppId = instructions.getMainAppId();
            if (mainAppId == null || mainAppId.isBlank()) {
                throw new IllegalArgumentException("Missing required mainAppId in download instructions");
            }

            JsonObject notification = new JsonObject();
            notification.addProperty("fileId", instructions.getFileId());
            notification.addProperty("operationId", instructions.getOperationId());
            notification.addProperty("correlationId", instructions.getCorrelationId());
            notification.addProperty("mainAppId", mainAppId);
            notification.addProperty("status", "ready");
            notification.addProperty("filename", reassembledFile.getName());
            notification.addProperty("aggregatorId", config.getAggregatorId());

            String topic = TopicConstants.downloadComplete(mainAppId);
            mqttBroker.publish(topic, notification.toString());

            System.out.println("[MqttMessageHandler] Sent download complete notification to topic: " + topic +
                               " for fileId: " + instructions.getFileId());

        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to send download notification: " + e.getMessage());
        }
    }
}
