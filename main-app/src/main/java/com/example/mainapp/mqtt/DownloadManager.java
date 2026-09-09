// src/main/java/com/example/mainapp/mqtt/DownloadManager.java
package com.example.mainapp.mqtt;

import com.example.mainapp.model.File;
import com.example.mainapp.model.FileChunkMetadata;
import com.example.mainapp.repository.FileRepository;
import com.example.mainapp.sftp.SftpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
        System.out.println("[DownloadManager] Starting download for file " + fileId);

        // Step 1: Verify and get file metadata
        File file = fileRepository.findById(fileId)
            .orElseThrow(() -> new Exception("File not found"));
        
        if (!isAdmin && file.getOwnerId() != userId) {
            throw new Exception("Permission denied");
        }

        // Get chunk metadata
        List<FileChunkMetadata> chunks = fileRepository.getChunksForFile(fileId);
        if (chunks.isEmpty()) {
            throw new Exception("No chunks found for file " + fileId);
        }

        System.out.println("[DownloadManager] Found " + chunks.size() + " chunks");

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

            JsonObject downloadRequest = new JsonObject();
            downloadRequest.addProperty("operation", "DOWNLOAD");
            downloadRequest.addProperty("fileId", fileId);
            downloadRequest.addProperty("userId", userId);
            downloadRequest.addProperty("mainAppId", mainAppId);  // FIXED: Add mainAppId
            downloadRequest.add("chunks", chunkLocations);

            // FIXED: Use operations/request and operations/response/{mainAppId}
            String responseTopic = "operations/response/" + mainAppId;
            String lbResponse = mqttClient.publishAndWaitForResponse(
                "operations/request",  // FIXED: Correct topic
                responseTopic,
                downloadRequest.toString(),
                10000  // 10s timeout
            );

            if (lbResponse == null) {
                throw new Exception("Load Balancer did not respond to download request");
            }

            // Step 3: Parse LB response
            JsonObject lbJson = mqttClient.getGson().fromJson(lbResponse, JsonObject.class);
            
            if (!lbJson.has("success") || !lbJson.get("success").getAsBoolean()) {
                String error = lbJson.has("error") ? lbJson.get("error").getAsString() : "Unknown error";
                throw new Exception("Load Balancer rejected download: " + error);
            }

            JsonObject aggregatorInfo = lbJson.getAsJsonObject("aggregator");
            JsonArray selectedFSContainers = lbJson.getAsJsonArray("fsContainers");  // LB selected these

            String aggregatorIp = aggregatorInfo.get("ip").getAsString();
            int aggregatorPort = aggregatorInfo.get("port").getAsInt();
            String aggregatorId = aggregatorInfo.get("id").getAsString();

            System.out.println("[DownloadManager] Selected Aggregator: " + aggregatorId);

            // Step 4: Create retrieval_instructions.json with LB-selected FS containers
            JsonObject instructions = new JsonObject();
            instructions.addProperty("fileId", fileId);
            instructions.addProperty("filename", file.getFilename());
            instructions.addProperty("mainAppId", mainAppId);  // Aggregator reads this to route the completion notification
            instructions.add("chunks", selectedFSContainers);  // FIXED: Use LB's selection

            Path instructionsPath = Paths.get("data/temp/downloads/retrieval_instructions_" + fileId + ".json");
            Files.createDirectories(instructionsPath.getParent());
            Files.writeString(instructionsPath, instructions.toString());

            System.out.println("[DownloadManager] Created retrieval instructions");

            // Step 5: Send instructions to Aggregator via SFTP
            SftpClient sftpClient = new SftpClient(mainAppId);
            try {
                sftpClient.connect(aggregatorIp, aggregatorPort, "sftpuser", "sftppass");
                sftpClient.uploadFile(instructionsPath, "retrieval_instructions.json");
                System.out.println("[DownloadManager] Sent retrieval instructions to Aggregator");
            } finally {
                sftpClient.disconnect();
            }

            // Step 6: Wait for Aggregator to complete reassembly
            // FIXED: Listen on aggregator/download/complete/{mainAppId}
            String aggCompleteTopic = "aggregator/download/complete/" + mainAppId;
            String aggNotify = mqttClient.waitForMessage(aggCompleteTopic, 120000);  // 2 min timeout

            if (aggNotify == null) {
                throw new Exception("Aggregator did not complete download processing within timeout");
            }

            // Verify this is for our file
            JsonObject aggJson = mqttClient.getGson().fromJson(aggNotify, JsonObject.class);
            long responseFileId = aggJson.get("fileId").getAsLong();
            if (responseFileId != fileId) {
                System.out.println("[DownloadManager] Ignoring response for different fileId: " + responseFileId);
                // Wait again
                aggNotify = mqttClient.waitForMessage(aggCompleteTopic, 120000);
                if (aggNotify == null) {
                    throw new Exception("Aggregator did not respond for our fileId");
                }
            }

            System.out.println("[DownloadManager] Aggregator completed reassembly");

            // Step 7: Download reassembled file from Aggregator
            Path downloadedFile = Paths.get("data/temp/downloads/" + fileId + "_" + file.getFilename());
            Files.createDirectories(downloadedFile.getParent());

            SftpClient downloadClient = new SftpClient(mainAppId);
            try {
                downloadClient.connect(aggregatorIp, aggregatorPort, "sftpuser", "sftppass");
                
                // FIXED: Use standard reassembled filename from Aggregator
                String remoteFilename = "fileId_" + fileId + "_reassembled.bin";
                downloadClient.downloadFile(remoteFilename, downloadedFile);
                
                System.out.println("[DownloadManager] Downloaded reassembled file");
            } finally {
                downloadClient.disconnect();
            }

            // Cleanup temp files
            Files.deleteIfExists(instructionsPath);

            System.out.println("[DownloadManager] Download complete: " + downloadedFile);
            return downloadedFile;

        } catch (Exception e) {
            System.err.println("[DownloadManager] Download failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
