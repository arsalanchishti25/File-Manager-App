// src/main/java/com/example/mainapp/mqtt/DeleteManager.java
package com.example.mainapp.mqtt;

import com.example.mainapp.model.File;
import com.example.mainapp.model.FileChunkMetadata;
import com.example.mainapp.repository.FileRepository;
import com.example.mainapp.service.PermissionService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Handles file deletion flow: Main -> LB -> FS containers
 * 
 * FIXED: Now goes through Load Balancer instead of direct FS communication
 */
public class DeleteManager {

    private final MqttClient mqttClient;
    private final FileRepository fileRepository;
    private final PermissionService permissionService;
    private final String mainAppId;

    public DeleteManager(MqttClient mqttClient, FileRepository fileRepository, 
                        PermissionService permissionService, String mainAppId) {
        this.mqttClient = mqttClient;
        this.fileRepository = fileRepository;
        this.permissionService = permissionService;
        this.mainAppId = mainAppId;
    }

    /**
     * Delete a file from distributed storage.
     * 
     * FLOW:
     * 1. Verify ownership
     * 2. Get chunk locations from MySQL (volume groups)
     * 3. Send MQTT delete request to Load Balancer (operations/request)
     * 4. Load Balancer broadcasts to all FS containers in each volume group
     * 5. Wait for LB confirmation (operations/response/{mainAppId})
     * 6. Delete metadata from MySQL
     */
    public void deleteFile(long fileId, long userId, boolean isAdmin) throws Exception {
        System.out.println("[DeleteManager] Starting delete for file " + fileId);

        // Step 1: Verify ownership
        File file = fileRepository.findById(fileId)
            .orElseThrow(() -> new Exception("File not found"));
        
        if (!isAdmin && file.getOwnerId() != userId) {
            throw new Exception("Permission denied");
        }

        // Step 2: Get chunk metadata (need volume groups)
        List<FileChunkMetadata> chunks = fileRepository.getChunksForFile(fileId);
        System.out.println("[DeleteManager] Found " + chunks.size() + " chunks to delete");

        try {
            // Step 3: Build chunk info array with volume groups
            JsonArray chunkInfo = new JsonArray();
            for (FileChunkMetadata chunk : chunks) {
                JsonObject chunkObj = new JsonObject();
                chunkObj.addProperty("chunkOrder", chunk.getChunkOrder());
                chunkObj.addProperty("volumeGroup", chunk.getVolumeGroup());
                chunkInfo.add(chunkObj);
            }

            // Send MQTT delete request to Load Balancer
            // FIXED: Use operations/request (not direct to FS containers)
            OperationRequest operationRequest = new OperationRequest("DELETE", mainAppId, fileId, userId);
            JsonObject deleteRequest = operationRequest.toJson();
            deleteRequest.addProperty("operation", "DELETE");
            deleteRequest.addProperty("fileId", fileId);
            deleteRequest.addProperty("userId", userId);
            deleteRequest.addProperty("mainAppId", mainAppId);
            deleteRequest.add("chunks", chunkInfo);

            // FIXED: Wait for LB confirmation on operations/response/{mainAppId}
            String responseTopic = TopicConstants.operationsResponse(mainAppId);
            String lbResponse = mqttClient.publishAndWaitForResponse(
                TopicConstants.OPERATIONS_REQUEST,
                responseTopic,
                deleteRequest.toString(),
                30000  // 30s timeout (LB needs to coordinate with multiple FS containers)
            );

            if (lbResponse == null) {
                throw new Exception("Load Balancer did not respond to delete request");
            }

            // Step 4: Parse LB response
            JsonObject lbJson = mqttClient.getGson().fromJson(lbResponse, JsonObject.class);
            
            if (!lbJson.has("success") || !lbJson.get("success").getAsBoolean()) {
                String error = lbJson.has("message") ? lbJson.get("message").getAsString() : "Unknown error";
                throw new Exception("Load Balancer failed to delete: " + error);
            }

            System.out.println("[DeleteManager] Load Balancer confirmed deletion from FS containers");

            // Step 5: Delete metadata from MySQL
            permissionService.deletePermissionsForFile(fileId);
            fileRepository.deleteChunksByFileId(fileId);
            fileRepository.deleteById(fileId);

            System.out.println("[DeleteManager] File " + fileId + " deleted successfully");

        } catch (Exception e) {
            System.err.println("[DeleteManager] Delete failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
