package com.example.aggregator.service;

import com.example.aggregator.model.UploadInstructions;
import com.example.aggregator.config.AggregatorAppConfig;
import com.example.aggregator.sftp.SftpClient;
// import com.google.gson.Gson;
import javax.crypto.SecretKey;
import java.io.File;
// import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;

/**
 * Handles file upload workflow:
 * 1. Receive file from Main App
 * 2. Split into 4 chunks
 * 3. Encrypt each chunk
 * 4. Calculate CRC32 checksums
 * 5. Upload chunks to FS containers
 */
public class UploadHandler {

    private final ChunkService chunkService;
    private final EncryptionService encryptionService;
    private final String workingDir;
    private final String sftpUser;
    private final String sftpPassword;
    // private final Gson gson;

    public UploadHandler(String workingDir, AggregatorAppConfig config) {
        this.workingDir = workingDir;
        this.chunkService = new ChunkService();
        this.encryptionService = new EncryptionService(config);
        this.sftpUser = config.requireSftpUser();
        this.sftpPassword = config.requireSftpPassword();
        // this.gson = new Gson();
    }

    /**
     * Process upload operation.
     * Returns list of CRC32 checksums for each chunk.
     */
    public List<String> processUpload(File originalFile, UploadInstructions instructions) throws Exception {
        if (instructions.getFsContainers() == null || instructions.getFsContainers().size() != 4) {
            throw new IllegalArgumentException("Upload requires exactly four file-storage targets");
        }

        System.out.println("\n[UploadHandler] ═══ Starting Upload Process ═══");
        System.out.println("  File ID: " + instructions.getFileId());
        System.out.println("  Filename: " + instructions.getFilename());
        System.out.println("  File size: " + originalFile.length() + " bytes");

        // Step 1: Split file into 4 chunks
        System.out.println("\n[UploadHandler] Step 1: Splitting file...");
        File[] chunks = chunkService.splitFile(originalFile, workingDir);

        // Step 2: Encrypt each chunk
        System.out.println("\n[UploadHandler] Step 2: Encrypting chunks...");
        SecretKey encryptionKey = encryptionService.getConfiguredKey();
        File[] encryptedChunks = new File[4];
        String stagingToken = instructions.isUpdateExisting()
                ? instructions.getStagingToken() : null;
        List<String> promotedFiles = new ArrayList<>();
        List<String> backups = new ArrayList<>();
        
        for (int i = 0; i < 4; i++) {
            byte[] chunkData = Files.readAllBytes(chunks[i].toPath());
            byte[] encryptedData = encryptionService.encrypt(chunkData, encryptionKey);
            
            String encryptedFilename = stagingToken == null
                    ? String.format("fileId_%d_chunk%d.enc", instructions.getFileId(), i + 1)
                    : String.format("fileId_%d_chunk%d.%s.tmp", instructions.getFileId(), i + 1,
                    stagingToken);
            File encryptedFile = new File(workingDir, encryptedFilename);
            
            try (FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                fos.write(encryptedData);
            }
            
            encryptedChunks[i] = encryptedFile;
            System.out.println("  Encrypted chunk " + (i + 1) + ": " + encryptedFile.getName());
        }

        // Step 3: Calculate CRC32 checksums
        System.out.println("\n[UploadHandler] Step 3: Calculating checksums...");
        List<String> checksums = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String crc32 = chunkService.calculateCRC32(encryptedChunks[i]);
            checksums.add(crc32);
        }

        // Step 4: Upload chunks to FS containers
        System.out.println("\n[UploadHandler] Step 4: Uploading chunks to FS containers...");
        for (int i = 0; i < 4; i++) {
            UploadInstructions.FSTarget target = instructions.getFsContainers().get(i);
            if (target.getFsId() == null || target.getFsId().isBlank()
                    || target.getChunkOrder() != i + 1
                    || target.getIp() == null || target.getIp().isBlank()
                    || target.getPort() <= 0) {
                throw new IllegalArgumentException("Invalid file-storage target for chunk " + (i + 1));
            }
            
            System.out.println("  Uploading chunk " + (i + 1) + " to " + target.getFsId() + 
                             " (" + target.getIp() + ":" + target.getPort() + ")");

            // TODO: Implement actual SFTP upload
            uploadChunkToFS(encryptedChunks[i], target);
        }

        if (stagingToken != null) {
            try {
                for (int i = 0; i < 4; i++) {
                    UploadInstructions.FSTarget target = instructions.getFsContainers().get(i);
                    promoteChunk(target, instructions.getFileId(), i + 1, stagingToken,
                            backups, promotedFiles);
                }
            } catch (Exception promotionFailure) {
                for (int i = 0; i < 4; i++) {
                    rollbackChunk(instructions.getFsContainers().get(i),
                            instructions.getFileId(), i + 1, stagingToken);
                }
                throw promotionFailure;
            }
            for (int i = 0; i < 4; i++) {
                UploadInstructions.FSTarget target = instructions.getFsContainers().get(i);
                deleteRemote(target, backupName(instructions.getFileId(), i + 1, stagingToken));
            }
        }

        // Step 5: Cleanup temporary files
        System.out.println("\n[UploadHandler] Step 5: Cleaning up temporary files...");
        for (File chunk : chunks) {
            chunk.delete();
        }
        for (File encChunk : encryptedChunks) {
            encChunk.delete();
        }
        originalFile.delete();

        System.out.println("\n[UploadHandler] ═══ Upload Complete ═══\n");
        return checksums;
    }

    /**
     * Upload a chunk to FS container via SFTP.
     * TODO: Implement actual SFTP connection and upload
     */
    private void uploadChunkToFS(File chunkFile, UploadInstructions.FSTarget target) throws Exception {
        SftpClient sftpClient = new SftpClient(target.getIp(), target.getPort(),
                sftpUser, sftpPassword);
        
        try {
            sftpClient.connect();
            sftpClient.uploadFile(chunkFile.getAbsolutePath(), chunkFile.getName());
        } catch (Exception e) {
            System.err.println("[UploadHandler] Failed to upload to " + target.getFsId() + ": " + e.getMessage());
            throw e;
        } finally {
            sftpClient.disconnect();
        }
    }

    private void promoteChunk(UploadInstructions.FSTarget target, long fileId, int order,
                              String token, List<String> backups, List<String> promoted)
            throws Exception {
            String canonical = String.format("fileId_%d_chunk%d.enc", fileId, order);
            String staged = String.format("fileId_%d_chunk%d.%s.tmp", fileId, order, token);
            String backup = backupName(fileId, order, token);
            SftpClient client = new SftpClient(target.getIp(), target.getPort(), sftpUser, sftpPassword);
            try {
                client.connect();
                try {
                    client.renameFile(canonical, backup);
                    backups.add(backup);
                } catch (Exception ignored) {
                    // No old chunk is expected for a newly created file.
                }
                client.renameFile(staged, canonical);
                promoted.add(canonical);
            } catch (Exception e) {
                throw e;
            } finally {
                client.disconnect();
            }
    }

    private void deleteRemote(UploadInstructions.FSTarget target, String filename) {
        SftpClient client = new SftpClient(target.getIp(), target.getPort(), sftpUser, sftpPassword);
        try {
            client.connect();
            client.deleteFile(filename);
        } catch (Exception ignored) {
            // Cleanup must not hide the completed upload result.
        } finally {
            client.disconnect();
        }

    }

    private void rollbackChunk(UploadInstructions.FSTarget target, long fileId, int order,
                               String token) {
            String canonical = String.format("fileId_%d_chunk%d.enc", fileId, order);
            String backup = backupName(fileId, order, token);
            SftpClient client = new SftpClient(target.getIp(), target.getPort(), sftpUser, sftpPassword);
            try {
                client.connect();
                try {
                    client.renameFile(canonical, canonical + "." + token + ".failed");
                } catch (Exception ignored) {
                    // The staged promotion may not have reached this target.
                }
                try {
                    client.renameFile(backup, canonical);
                } catch (Exception ignored) {
                    // There may not have been an old chunk to restore.
                }
            } catch (Exception ignored) {
                // Preserve the original promotion failure for the completion response.
            } finally {
                client.disconnect();
        }
    }

    private String backupName(long fileId, int order, String token) {
        return String.format("fileId_%d_chunk%d.%s.bak", fileId, order, token);
    }
}
