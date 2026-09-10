// src/main/java/com/example/mainapp/mqtt/UploadManager.java
package com.example.mainapp.mqtt;

import com.example.mainapp.config.MainAppConfig;
import com.example.mainapp.model.File;
import com.example.mainapp.repository.FileRepository;
import com.example.mainapp.sftp.SftpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles file upload flow: Main -> LB -> Aggregator -> FS containers
 * 
 * FIXED: Now uses correct MQTT topics matching Load Balancer design
 */
public class UploadManager {

    private final MqttClient mqttClient;
    private final FileRepository fileRepository;
    private final String mainAppId;

    public UploadManager(MqttClient mqttClient, FileRepository fileRepository, String mainAppId) {
        this.mqttClient = mqttClient;
        this.fileRepository = fileRepository;
        this.mainAppId = mainAppId;
    }

    /**
     * Start file upload process.
     * 
     * FLOW:
     * 1. Save file metadata (status=UPLOADING)
     * 2. Send MQTT request to Load Balancer (operations/request)
     * 3. Receive LB response (operations/response/{mainAppId})
     * 4. Create instructions.json with FS container details
     * 5. Send file + instructions.json to Aggregator via SFTP
     * 6. Wait for Aggregator completion (aggregator/upload/complete/{mainAppId})
     * 7. Save chunk metadata
     * 8. Update file status to READY
     */
    public File uploadFile(long ownerId, Path sourcePath, long fileSize) throws Exception {
        String filename = sourcePath.getFileName().toString();
        
        System.out.println("[UploadManager] Starting upload: " + filename + " (" + fileSize + " bytes)");

        // Step 1: Save metadata with UPLOADING status
        File file = fileRepository.saveFile(ownerId, filename, fileSize);
        // fileRepository.updateFileStatus(file.getId(), "UPLOADING");
        
        System.out.println("[UploadManager] File metadata saved with ID: " + file.getId());

        try {
            // Step 2: Send MQTT request to Load Balancer
            // Use operations/request topic and include mainAppId
            JsonObject uploadRequest = new JsonObject();
            uploadRequest.addProperty("operation", "UPLOAD");
            uploadRequest.addProperty("fileId", file.getId());
            uploadRequest.addProperty("userId", ownerId);
            uploadRequest.addProperty("fileSize", fileSize);
            uploadRequest.addProperty("filename", filename);
            uploadRequest.addProperty("mainAppId", mainAppId);  // FIXED: Add mainAppId

            // FIXED: Wait for response on operations/response/{mainAppId}
            String responseTopic = "operations/response/" + mainAppId;
            String lbResponse = mqttClient.publishAndWaitForResponse(
                "operations/request",  // FIXED: Correct topic
                responseTopic,
                uploadRequest.toString(),
                10000  // 10s timeout
            );

            if (lbResponse == null) {
                throw new Exception("Load Balancer did not respond to upload request");
            }

            System.out.println("[UploadManager] Received LB response");

            // Step 3: Parse LB response
            JsonObject lbJson = mqttClient.getGson().fromJson(lbResponse, JsonObject.class);
            
            if (!lbJson.has("success") || !lbJson.get("success").getAsBoolean()) {
                String error = lbJson.has("error") ? lbJson.get("error").getAsString() : "Unknown error";
                throw new Exception("Load Balancer rejected upload: " + error);
            }

            JsonObject aggregatorInfo = lbJson.getAsJsonObject("aggregator");
            JsonArray fsContainersArray = lbJson.getAsJsonArray("fsContainers");

            String aggregatorIp = aggregatorInfo.get("ip").getAsString();
            int aggregatorPort = aggregatorInfo.get("port").getAsInt();
            String aggregatorId = aggregatorInfo.get("id").getAsString();  // FIXED: Use "id" not "name"

            System.out.println("[UploadManager] Selected Aggregator: " + aggregatorId + " (" + aggregatorIp + ":" + aggregatorPort + ")");

            // Step 4: Create instructions.json (Aggregator expects this filename)
            // FIXED: Rename from "manifest" to "instructions" to match Aggregator expectations
            JsonObject instructions = new JsonObject();
            instructions.addProperty("fileId", file.getId());
            instructions.addProperty("filename", filename);
            instructions.addProperty("fileSize", fileSize);
            instructions.addProperty("mainAppId", mainAppId);  // Aggregator reads this to route the completion notification
            instructions.add("fsContainers", fsContainersArray);

            // FIXED: Save as instructions.json (not manifest.json)
            Path instructionsPath = Paths.get("/data/temp/uploads/instructions_" + file.getId() + ".json");
            Files.createDirectories(instructionsPath.getParent());
            Files.writeString(instructionsPath, instructions.toString());

            System.out.println("[UploadManager] Created instructions file");

            // Step 5: Send file + instructions to Aggregator via SFTP
            // FIXED: Use standard naming that Aggregator expects
            SftpClient sftpClient = new SftpClient(mainAppId);
            try {
                MainAppConfig config = MainAppConfig.getInstance();
                sftpClient.connect(aggregatorIp, aggregatorPort,
                        config.requireSftpUser(), config.requireSftpPassword());
                
                // Upload original file with standard naming
                String remoteFilename = "fileId_" + file.getId() + ".bin";
                sftpClient.uploadFile(sourcePath, remoteFilename);
                
                // Upload instructions
                sftpClient.uploadFile(instructionsPath, "instructions.json");
                
                System.out.println("[UploadManager] Sent file and instructions to Aggregator");
                
            } finally {
                sftpClient.disconnect();
            }

            // Step 6: Wait for Aggregator completion MQTT message
            // FIXED: Listen on aggregator/upload/complete/{mainAppId} (not {fileId})
            String aggResponseTopic = "aggregator/upload/complete/" + mainAppId;
            
            // FIXED: Don't send "started" notification - Aggregator monitors SFTP directory
            // Just subscribe and wait for completion message
            String aggResponse = mqttClient.waitForMessage(aggResponseTopic, 60000);

            if (aggResponse == null) {
                throw new Exception("Aggregator did not complete upload processing within timeout");
            }

            System.out.println("[UploadManager] Aggregator completed processing");

            // Step 7: Parse aggregator response and save chunk metadata
            JsonObject aggJson = mqttClient.getGson().fromJson(aggResponse, JsonObject.class);
            
            // Verify this is for our file
            long responseFileId = aggJson.get("fileId").getAsLong();
            if (responseFileId != file.getId()) {
                System.out.println("[UploadManager] Ignoring response for different fileId: " + responseFileId);
                // Wait again for our file's response
                aggResponse = mqttClient.waitForMessage(aggResponseTopic, 60000);
                if (aggResponse == null) {
                    throw new Exception("Aggregator did not respond for our fileId");
                }
                aggJson = mqttClient.getGson().fromJson(aggResponse, JsonObject.class);
            }

            JsonArray chunksArray = aggJson.getAsJsonArray("chunks");

            // FIXED: Save chunk metadata with volumeGroup
            for (int i = 0; i < chunksArray.size(); i++) {
                JsonObject chunk = chunksArray.get(i).getAsJsonObject();
                int chunkOrder = chunk.get("chunkOrder").getAsInt();
                int volumeGroup = chunk.get("volumeGroup").getAsInt();
                String fsId = chunk.get("fsId").getAsString();  // FIXED: Get fsId not fsContainer
                String crc32 = chunk.get("crc32").getAsString();

                // Save: fileId, chunkOrder, crc32, storageLocation (fsId), volumeGroup
                fileRepository.saveChunk(file.getId(), chunkOrder, crc32, fsId, volumeGroup);
                
                System.out.println("[UploadManager] Saved chunk metadata: order=" + chunkOrder 
                                 + ", fsId=" + fsId + ", volumeGroup=" + volumeGroup);
            }

            // Step 8: Update file status to READY
            // fileRepository.updateFileStatus(file.getId(), "READY");
            System.out.println("[UploadManager] Upload complete for file " + file.getId());

            // Cleanup temp files
            Files.deleteIfExists(instructionsPath);

            return file;

        } catch (Exception e) {
            // fileRepository.updateFileStatus(file.getId(), "FAILED");
            System.err.println("[UploadManager] Upload failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
