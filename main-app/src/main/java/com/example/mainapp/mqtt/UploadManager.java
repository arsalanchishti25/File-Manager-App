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
import java.util.HashSet;
import java.util.Set;

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
        MqttClient.MessageWaiter completionWaiter = null;
        // fileRepository.updateFileStatus(file.getId(), "UPLOADING");
        
        System.out.println("[UploadManager] File metadata saved with ID: " + file.getId());

        try {
            // Step 2: Send MQTT request to Load Balancer
            // Use operations/request topic and include mainAppId
            OperationRequest operationRequest = new OperationRequest("UPLOAD", mainAppId, file.getId(), ownerId);
            JsonObject uploadRequest = operationRequest.toJson();
            uploadRequest.addProperty("userId", ownerId);
            uploadRequest.addProperty("fileSize", fileSize);
            uploadRequest.addProperty("filename", filename);
            uploadRequest.addProperty("operationId", operationRequest.getOperationId());
            uploadRequest.addProperty("correlationId", operationRequest.getCorrelationId());

            // FIXED: Wait for response on operations/response/{mainAppId}
            String responseTopic = TopicConstants.operationsResponse(mainAppId);
            String lbResponse = mqttClient.publishAndWaitForResponse(
                TopicConstants.OPERATIONS_REQUEST,
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
            instructions.addProperty("userId", ownerId);
            instructions.addProperty("operationId", operationRequest.getOperationId());
            instructions.addProperty("correlationId", operationRequest.getCorrelationId());
            instructions.addProperty("sourceServiceId", mainAppId);
            instructions.add("fsContainers", fsContainersArray);

            // FIXED: Save as instructions.json (not manifest.json)
            Path instructionsPath = Paths.get("/data/temp/uploads/instructions_" + file.getId() + ".json");
            Files.createDirectories(instructionsPath.getParent());
            Files.writeString(instructionsPath, instructions.toString());

            System.out.println("[UploadManager] Created instructions file");
            String aggResponseTopic = TopicConstants.uploadComplete(mainAppId);
            completionWaiter = mqttClient.prepareMessageWait(aggResponseTopic);

            // Step 5: Send the file via SFTP, then deliver the instruction over MQTT
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
            mqttClient.publish(TopicConstants.aggregator(aggregatorId, "upload"), instructions.toString());

            // Step 6: Wait for Aggregator completion MQTT message
            String aggResponse = completionWaiter.await(60000);

            if (aggResponse == null) {
                throw new Exception("Aggregator did not complete upload processing within timeout");
            }

            System.out.println("[UploadManager] Aggregator completed processing");

            // Step 7: Parse aggregator response and save chunk metadata
            System.out.println("[UploadManager] Received completion on " + aggResponseTopic
                    + ": " + aggResponse);
            JsonObject aggJson = parseCompletionPayload(aggResponse);
            requireObject(aggJson, "completion response");
            System.out.println("[UploadManager] Completion fields: " + aggJson.entrySet());
            
            // Verify this is for our file
            long responseFileId = requireLong(aggJson, "fileId");
            String responseOperationId = requireString(aggJson, "operationId");
            String responseCorrelationId = requireString(aggJson, "correlationId");
            String responseMainAppId = requireString(aggJson, "mainAppId");
            String responseStatus = requireString(aggJson, "status");
            validateCompletionStatus(aggJson, responseStatus);
            if (!operationRequest.getOperationId().equals(responseOperationId)
                    || !operationRequest.getCorrelationId().equals(responseCorrelationId)
                    || !mainAppId.equals(responseMainAppId)
                    || !"complete".equalsIgnoreCase(responseStatus)) {
                throw new Exception("Aggregator returned an invalid upload completion");
            }
            if (responseFileId != file.getId()) {
                throw new Exception("Aggregator returned completion for unexpected fileId: " + responseFileId);
            }

            JsonArray chunksArray = requireArray(aggJson, "chunks");
            if (chunksArray.size() != 4) {
                throw new Exception("Aggregator returned an incomplete chunk set");
            }

            validateChunkMetadata(chunksArray);

            for (int i = 0; i < chunksArray.size(); i++) {
                JsonObject chunk = chunksArray.get(i).getAsJsonObject();
                int chunkOrder = requireInt(chunk, "chunkOrder");
                int volumeGroup = requireInt(chunk, "volumeGroup");
                String fsId = requireString(chunk, "fsId");
                String crc32 = requireString(chunk, "crc32");

                // Save: fileId, chunkOrder, crc32, storageLocation (fsId), volumeGroup
                fileRepository.saveChunk(file.getId(), chunkOrder, crc32, fsId, volumeGroup);
                
                System.out.println("[UploadManager] Saved chunk metadata: order=" + chunkOrder 
                                 + ", fsId=" + fsId + ", volumeGroup=" + volumeGroup);
            }

            // Step 8: Update file status to READY
            System.out.println("[UploadManager] Upload complete for file " + file.getId());

            // Cleanup temp files
            Files.deleteIfExists(instructionsPath);

            return file;

        } catch (Exception e) {
            if (completionWaiter != null) {
                completionWaiter.close();
            }
            fileRepository.deleteById(file.getId());
            System.err.println("[UploadManager] Upload failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    static void requireObject(JsonObject object, String field) throws Exception {
        if (object == null) {
            throw new Exception("Aggregator completion response is missing required field: " + field);
        }
    }

    static JsonObject parseCompletionPayload(String payload) throws Exception {
        if (payload == null || payload.isBlank()) {
            throw new Exception("Aggregator completion response is missing");
        }
        try {
            JsonObject object = new com.google.gson.Gson().fromJson(payload, JsonObject.class);
            requireObject(object, "completion response");
            return object;
        } catch (com.google.gson.JsonParseException | UnsupportedOperationException e) {
            throw new Exception("Malformed Aggregator completion response", e);
        }
    }

    static String requireString(JsonObject object, String field) throws Exception {
        if (!object.has(field) || object.get(field).isJsonNull()
                || !object.get(field).isJsonPrimitive()
                || object.get(field).getAsString().isBlank()) {
            throw new Exception("Aggregator completion response is missing required field: " + field);
        }
        return object.get(field).getAsString();
    }

    static long requireLong(JsonObject object, String field) throws Exception {
        if (!object.has(field) || object.get(field).isJsonNull()
                || !object.get(field).isJsonPrimitive()) {
            throw new Exception("Aggregator completion response is missing required field: " + field);
        }
        try {
            return object.get(field).getAsLong();
        } catch (RuntimeException e) {
            throw new Exception("Aggregator completion response has invalid field: " + field, e);
        }
    }

    static int requireInt(JsonObject object, String field) throws Exception {
        long value = requireLong(object, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new Exception("Aggregator completion response has invalid field: " + field);
        }
        return (int) value;
    }

    static JsonArray requireArray(JsonObject object, String field) throws Exception {
        if (!object.has(field) || object.get(field).isJsonNull()
                || !object.get(field).isJsonArray()) {
            throw new Exception("Aggregator completion response is missing required field: " + field);
        }
        return object.getAsJsonArray(field);
    }

    static void validateCompletionStatus(JsonObject object, String status) throws Exception {
        if ("failed".equalsIgnoreCase(status)) {
            throw new Exception("Aggregator upload failed: " + optionalString(object, "error"));
        }
        if (!"complete".equalsIgnoreCase(status)) {
            throw new Exception("Aggregator returned an invalid upload completion status: " + status);
        }
    }

    static void validateChunkMetadata(JsonArray chunks) throws Exception {
        Set<Integer> chunkOrders = new HashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (!chunks.get(i).isJsonObject()) {
                throw new Exception("Aggregator completion response has invalid field: chunks[" + i + "]");
            }
            JsonObject chunk = chunks.get(i).getAsJsonObject();
            int order = requireInt(chunk, "chunkOrder");
            if (order <= 0) {
                throw new Exception("Invalid chunkOrder in completion response: " + order);
            }
            if (!chunkOrders.add(order)) {
                throw new Exception("Duplicate chunkOrder in completion response: " + order);
            }
            int volumeGroup = requireInt(chunk, "volumeGroup");
            if (volumeGroup <= 0) {
                throw new Exception("Invalid volumeGroup in completion response: " + volumeGroup);
            }
            requireString(chunk, "fsId");
            requireString(chunk, "crc32");
        }
        for (int expected = 1; expected <= chunks.size(); expected++) {
            if (!chunkOrders.contains(expected)) {
                throw new Exception("Non-contiguous chunkOrder in completion response; missing: " + expected);
            }
        }
    }

    private static String optionalString(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : "unspecified error";
    }
}
