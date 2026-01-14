package com.example.aggregator.mqtt;

import com.example.aggregator.model.AggregatorConfig;
import com.example.aggregator.model.UploadInstructions;
import com.example.aggregator.model.DownloadInstructions;
import com.example.aggregator.service.UploadHandler;
import com.example.aggregator.service.DownloadHandler;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.MqttException;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

    public MqttMessageHandler(MqttBroker mqttBroker, AggregatorConfig config) {
        this.mqttBroker = mqttBroker;
        this.config = config;
        this.uploadHandler = new UploadHandler(config.getWorkingDirectory());
        this.downloadHandler = new DownloadHandler(config.getWorkingDirectory());
    }

    /**
     * Start monitoring working directory for new files.
     */
    public void startMonitoring() {
        running = true;
        
        Thread monitorThread = new Thread(() -> {
            System.out.println("[MqttMessageHandler] Started monitoring: " + config.getWorkingDirectory());
            
            while (running) {
                try {
                    // Check for instructions.json files
                    Path workingPath = Paths.get(config.getWorkingDirectory());
                    
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(workingPath, "instructions*.json")) {
                        for (Path instructionsFile : stream) {
                            System.out.println("\n[MqttMessageHandler] Found instructions file: " + 
                                             instructionsFile.getFileName());
                            processInstructions(instructionsFile.toFile());
                        }
                    }
                    
                    // Check for retrieval_instructions.json files
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
     */
    private void sendUploadCompleteNotification(UploadInstructions instructions, List<String> checksums) {
        try {
            JsonObject notification = new JsonObject();
            notification.addProperty("fileId", instructions.getFileId());
            notification.addProperty("status", "complete");
            notification.addProperty("aggregatorId", config.getAggregatorId());

            // TODO: Get mainAppId from instructions
            String mainAppId = "main-app-1";  // Placeholder
            String topic = "aggregator/upload/complete/" + mainAppId;

            mqttBroker.publish(topic, notification.toString());

            System.out.println("[MqttMessageHandler] Sent upload complete notification for fileId: " + 
                             instructions.getFileId());

        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to send upload notification: " + e.getMessage());
        }
    }

    /**
     * Send download complete notification to Main App.
     * Topic: aggregator/download/complete/{mainAppId}
     */
    private void sendDownloadCompleteNotification(DownloadInstructions instructions, File reassembledFile) {
        try {
            JsonObject notification = new JsonObject();
            notification.addProperty("fileId", instructions.getFileId());
            notification.addProperty("status", "ready");
            notification.addProperty("filename", reassembledFile.getName());
            notification.addProperty("aggregatorId", config.getAggregatorId());

            // TODO: Get mainAppId from instructions
            String mainAppId = "main-app-1";  // Placeholder
            String topic = "aggregator/download/complete/" + mainAppId;

            mqttBroker.publish(topic, notification.toString());

            System.out.println("[MqttMessageHandler] Sent download complete notification for fileId: " + 
                             instructions.getFileId());

        } catch (MqttException e) {
            System.err.println("[MqttMessageHandler] Failed to send download notification: " + e.getMessage());
        }
    }
}
