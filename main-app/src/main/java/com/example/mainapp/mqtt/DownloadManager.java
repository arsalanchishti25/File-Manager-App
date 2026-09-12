// src/main/java/com/example/mainapp/mqtt/DownloadManager.java
package com.example.mainapp.mqtt;

import com.example.mainapp.config.MainAppConfig;
import com.example.mainapp.model.File;
import com.example.mainapp.model.FileChunkMetadata;
import com.example.mainapp.repository.FileRepository;
import com.example.mainapp.sftp.SftpClient;
import com.example.mainapp.service.FileOperationLock;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles file download flow: Main -> LB -> Aggregator -> Main
 * 
 * FIXED: Now uses correct MQTT topics and sends volume groups (not FS container names)
 */
public class DownloadManager {

    private final MqttClient mqttClient;
    private final FileRepository fileRepository;
    private final String mainAppId;

    public DownloadManager(MqttClient mqttClient, FileRepository fileRepository, String mainAppId) {
        this.mqttClient = mqttClient;
        this.fileRepository = fileRepository;
        this.mainAppId = mainAppId;
    }

    /**
     * Download a file from distributed storage.
     * 
     * FLOW:
     * 1. Get chunk metadata from MySQL (know volume groups)
     * 2. Send MQTT request to Load Balancer (operations/request)
     * 3. Receive LB response with available Aggregator and selected FS containers
     * 4. Create retrieval_instructions.json
     * 5. Send instructions to Aggregator via SFTP
     * 6. Wait for Aggregator to reassemble (aggregator/download/complete/{mainAppId})
     * 7. Download reassembled file via SFTP
     */
    public Path downloadFile(long fileId, long userId, boolean isAdmin) throws Exception {
        ReentrantLock fileLock = FileOperationLock.forFile(fileId);
        fileLock.lockInterruptibly();
        try {
            return downloadFileLocked(fileId, userId, isAdmin);
        } finally {
            fileLock.unlock();
        }
    }

    private Path downloadFileLocked(long fileId, long userId, boolean isAdmin) throws Exception {
        System.out.println("[DownloadManager] Starting download for file " + fileId);

        // Step 1: Verify and get file metadata
        File file = fileRepository.findById(fileId)
            .orElseThrow(() -> new Exception("File not found"));
        
        if (!isAdmin && file.getOwnerId() != userId) {
            throw new Exception("Permission denied");
        }

        // Get chunk metadata
        List<FileChunkMetadata> chunks = fileRepository.getChunksForFile(fileId);
        if (chunks.size() != 4) {
            throw new Exception("Incomplete chunk metadata for file " + fileId);
        }
        Set<Integer> orders = new HashSet<>();
        for (FileChunkMetadata chunk : chunks) {
            if (chunk.getChunkOrder() <= 0 || !orders.add(chunk.getChunkOrder())
                    || chunk.getVolumeGroup() <= 0
                    || chunk.getCrc32Checksum() == null || chunk.getCrc32Checksum().isBlank()) {
                throw new Exception("Invalid chunk metadata for file " + fileId);
            }
        }
        for (int order = 1; order <= chunks.size(); order++) {
            if (!orders.contains(order)) {
                throw new Exception("Non-contiguous chunk metadata for file " + fileId);
            }
        }
        if (file.getFilename() == null || file.getFilename().isBlank()
                || file.getFilename().contains("/") || file.getFilename().contains("\\")
                || ".".equals(file.getFilename()) || "..".equals(file.getFilename())) {
            throw new Exception("Invalid stored filename");
        }

        System.out.println("[DownloadManager] Found " + chunks.size() + " chunks");

        Path downloadedFile = null;
        try {
            // Step 2: Send MQTT request to Load Balancer
            // FIXED: Send volume groups (not specific FS container names)
            JsonArray chunkLocations = new JsonArray();
            for (FileChunkMetadata chunk : chunks) {
                JsonObject chunkObj = new JsonObject();
                chunkObj.addProperty("chunkOrder", chunk.getChunkOrder());
                chunkObj.addProperty("volumeGroup", chunk.getVolumeGroup());  // FIXED: Send volumeGroup
                chunkObj.addProperty("crc32", chunk.getCrc32Checksum());
                chunkLocations.add(chunkObj);
            }

            OperationRequest operationRequest = new OperationRequest("DOWNLOAD", mainAppId, fileId, userId);
            JsonObject downloadRequest = operationRequest.toJson();
            downloadRequest.addProperty("userId", userId);
            downloadRequest.addProperty("filename", file.getFilename());
            downloadRequest.addProperty("operationId", operationRequest.getOperationId());
            downloadRequest.addProperty("correlationId", operationRequest.getCorrelationId());
            downloadRequest.add("chunks", chunkLocations);

            // FIXED: Use operations/request and operations/response/{mainAppId}
            String responseTopic = TopicConstants.operationsResponse(mainAppId);
            String lbResponse = mqttClient.publishAndWaitForResponse(
                TopicConstants.OPERATIONS_REQUEST,
                responseTopic,
                downloadRequest.toString(),
                10000  // 10s timeout
            );

            if (lbResponse == null) {
                throw new Exception("Load Balancer did not respond to download request");
            }
            System.out.println("[DownloadManager] Load Balancer response received for fileId="
                    + fileId);

            // Step 3: Parse LB response
            JsonObject lbJson = mqttClient.getGson().fromJson(lbResponse, JsonObject.class);
            
            if (!lbJson.has("success") || !lbJson.get("success").getAsBoolean()) {
                String error = lbJson.has("message") ? lbJson.get("message").getAsString()
                        : lbJson.has("errorMessage") ? lbJson.get("errorMessage").getAsString()
                        : lbJson.has("error") ? lbJson.get("error").getAsString()
                        : "Unknown error";
                throw new Exception("Load Balancer rejected download: " + error);
            }

            JsonObject aggregatorInfo = lbJson.getAsJsonObject("aggregator");
            JsonArray selectedFSContainers = lbJson.getAsJsonArray("fsContainers");  // LB selected these
            if (aggregatorInfo == null || !aggregatorInfo.has("ip")
                    || aggregatorInfo.get("ip").isJsonNull()
                    || !aggregatorInfo.has("port")
                    || aggregatorInfo.get("port").isJsonNull()) {
                throw new Exception("Load Balancer returned incomplete aggregator connection details");
            }
            if (selectedFSContainers == null || selectedFSContainers.size() != chunks.size()) {
                throw new Exception("Load Balancer returned incomplete storage routing");
            }

            String aggregatorIp = aggregatorInfo.get("ip").getAsString();
            int aggregatorPort = aggregatorInfo.get("port").getAsInt();
            String aggregatorId = aggregatorInfo.get("id").getAsString();
            if (aggregatorIp.isBlank() || aggregatorPort <= 0) {
                throw new Exception("Load Balancer returned invalid aggregator connection details");
            }

            System.out.println("[DownloadManager] Selected Aggregator: " + aggregatorId);

            // Step 4: Create retrieval_instructions.json with LB-selected FS containers
            JsonObject instructions = new JsonObject();
            instructions.addProperty("fileId", fileId);
            instructions.addProperty("filename", file.getFilename());
            instructions.addProperty("mainAppId", mainAppId);  // Aggregator reads this to route the completion notification
            instructions.addProperty("operationId", operationRequest.getOperationId());
            instructions.addProperty("correlationId", operationRequest.getCorrelationId());
            instructions.addProperty("sourceServiceId", mainAppId);
            Map<Integer, FileChunkMetadata> metadataByOrder = new HashMap<>();
            for (FileChunkMetadata chunk : chunks) {
                metadataByOrder.put(chunk.getChunkOrder(), chunk);
            }
            JsonArray instructionChunks = new JsonArray();
            for (int i = 0; i < selectedFSContainers.size(); i++) {
                JsonObject selected = selectedFSContainers.get(i).getAsJsonObject();
                int order = selected.get("chunkOrder").getAsInt();
                FileChunkMetadata metadata = metadataByOrder.get(order);
                if (metadata == null) {
                    throw new Exception("Missing metadata for chunk order " + order);
                }
                selected.addProperty("fsId", metadata.getStorageLocation());
                selected.addProperty("crc32", metadata.getCrc32Checksum());
                instructionChunks.add(selected);
            }
            instructions.add("chunks", instructionChunks);

            Path instructionsPath = Paths.get("data/temp/downloads/retrieval_instructions_" + fileId + ".json");
            Files.createDirectories(instructionsPath.getParent());
            Files.writeString(instructionsPath, instructions.toString());

            System.out.println("[DownloadManager] Created retrieval instructions");

            // Step 5: Send retrieval instructions directly over MQTT.
            String aggCompleteTopic = TopicConstants.downloadComplete(mainAppId);
            MqttClient.MessageWaiter completionWaiter = mqttClient.prepareMessageWait(aggCompleteTopic);
            System.out.println("[DownloadManager] Aggregator completion subscription registered: "
                    + aggCompleteTopic);
            mqttClient.publish(TopicConstants.aggregator(aggregatorId, "download"), instructions.toString());

            // Step 6: Wait for Aggregator to complete reassembly
            String aggNotify = completionWaiter.await(120000);  // 2 min timeout

            if (aggNotify == null) {
                throw new Exception("Timed out waiting for aggregator completion");
            }

            System.out.println("[DownloadManager] Aggregator completion received: " + aggNotify);
            JsonObject aggJson = mqttClient.getGson().fromJson(aggNotify, JsonObject.class);
            if (aggJson == null || !aggJson.has("fileId") || !aggJson.has("operationId")
                    || !aggJson.has("correlationId") || !aggJson.has("status")
                    || !aggJson.has("filename") || aggJson.get("fileId").isJsonNull()
                    || aggJson.get("operationId").isJsonNull()
                    || aggJson.get("correlationId").isJsonNull()
                    || aggJson.get("status").isJsonNull()
                    || aggJson.get("filename").isJsonNull()) {
                throw new Exception("Aggregator completion did not match this request");
            }
            if ("failed".equalsIgnoreCase(aggJson.get("status").getAsString())) {
                String message = aggJson.has("message") && !aggJson.get("message").isJsonNull()
                        ? aggJson.get("message").getAsString() : "Aggregator download failed";
                throw new Exception(message);
            }
            long responseFileId = aggJson.get("fileId").getAsLong();
            if (responseFileId != fileId) {
                throw new Exception("Aggregator completion did not match this request");
            }
            if (!operationRequest.getOperationId().equals(aggJson.get("operationId").getAsString())
                    || !operationRequest.getCorrelationId().equals(aggJson.get("correlationId").getAsString())
                    || !"ready".equalsIgnoreCase(aggJson.get("status").getAsString())) {
                throw new Exception("Aggregator completion did not match this request");
            }
            String remoteFilename = "fileId_" + fileId + "_reassembled.bin";
            if (!remoteFilename.equals(aggJson.get("filename").getAsString())) {
                throw new Exception("Aggregator completion did not match this request");
            }
            System.out.println("[DownloadManager] Aggregator completion correlated: correlationId="
                    + aggJson.get("correlationId").getAsString());

            System.out.println("[DownloadManager] Aggregator completed reassembly");

            // Step 7: Download reassembled file from Aggregator
            downloadedFile = Paths.get("data/temp/downloads/" + fileId + "_" + file.getFilename());
            Files.createDirectories(downloadedFile.getParent());

            SftpClient downloadClient = new SftpClient(mainAppId);
            try {
                MainAppConfig config = MainAppConfig.getInstance();
                System.out.println("[DownloadManager] SFTP retrieval from aggregator started: "
                        + aggregatorIp + ":" + aggregatorPort);
                downloadClient.connect(aggregatorIp, aggregatorPort,
                        config.requireSftpUser(), config.requireSftpPassword());
                
                // FIXED: Use standard reassembled filename from Aggregator
                downloadClient.downloadFile(remoteFilename, downloadedFile);
                
                System.out.println("[DownloadManager] SFTP retrieval from aggregator completed");
            } finally {
                downloadClient.disconnect();
            }

            if (!Files.exists(downloadedFile) || Files.size(downloadedFile) == 0) {
                throw new Exception("Downloaded file is missing/empty");
            }
            System.out.println("[DownloadManager] Local temporary file: " + downloadedFile
                    + " (" + Files.size(downloadedFile) + " bytes)");

            // Cleanup temp files
            Files.deleteIfExists(instructionsPath);

            System.out.println("[DownloadManager] Download complete: " + downloadedFile);
            return downloadedFile;

        } catch (Exception e) {
            if (downloadedFile != null) {
                Files.deleteIfExists(downloadedFile);
            }
            System.err.println("[DownloadManager] Download failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
