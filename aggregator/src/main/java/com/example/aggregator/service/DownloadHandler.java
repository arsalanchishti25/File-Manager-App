package com.example.aggregator.service;

import com.example.aggregator.model.DownloadInstructions;
import com.example.aggregator.config.AggregatorAppConfig;
import com.example.aggregator.sftp.SftpClient;
import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Comparator;

/**
 * Handles file download workflow:
 * 1. Retrieve chunks from FS containers
 * 2. Verify CRC32 checksums
 * 3. Decrypt chunks
 * 4. Reassemble into original file
 */
public class DownloadHandler {

    private final ChunkService chunkService;
    private final EncryptionService encryptionService;
    private final String workingDir;
    private final String sftpUser;
    private final String sftpPassword;

    public DownloadHandler(String workingDir, AggregatorAppConfig config) {
        this.workingDir = workingDir;
        this.chunkService = new ChunkService();
        this.encryptionService = new EncryptionService(config);
        this.sftpUser = config.requireSftpUser();
        this.sftpPassword = config.requireSftpPassword();
    }

    /**
     * Process download operation.
     * Returns the reassembled file.
     */
    public File processDownload(DownloadInstructions instructions) throws Exception {
        if (instructions == null) {
            throw new IllegalArgumentException("Download instructions are required");
        }
        System.out.println("\n[DownloadHandler] ═══ Starting Download Process ═══");
        System.out.println("  File ID: " + instructions.getFileId());
        System.out.println("  Filename: " + instructions.getFilename());

        if (instructions.getFileId() <= 0
                || instructions.getChunks() == null || instructions.getChunks().size() != 4) {
            throw new IllegalArgumentException("Download requires four chunks");
        }
        instructions.getChunks().sort(Comparator.comparingInt(DownloadInstructions.ChunkLocation::getChunkOrder));
        for (int i = 0; i < instructions.getChunks().size(); i++) {
            if (instructions.getChunks().get(i).getChunkOrder() != i + 1
                    || instructions.getChunks().get(i).getFsId() == null
                    || instructions.getChunks().get(i).getFsId().isBlank()
                    || instructions.getChunks().get(i).getCrc32() == null
                    || instructions.getChunks().get(i).getCrc32().isBlank()) {
                throw new IllegalArgumentException("Invalid download chunk metadata at order " + (i + 1));
            }
        }

        File[] encryptedChunks = new File[4];
        File[] decryptedChunks = new File[4];
        File reassembledFile = null;
        boolean completed = false;
        try {
            // Step 1: Retrieve encrypted chunks from FS containers
            System.out.println("\n[DownloadHandler] Step 1: Retrieving chunks from FS containers...");
            for (int i = 0; i < 4; i++) {
                DownloadInstructions.ChunkLocation chunkLoc = instructions.getChunks().get(i);
            
            System.out.println("  Retrieving chunk " + (i + 1) + " from " + chunkLoc.getFsId() + 
                             " (" + chunkLoc.getIp() + ":" + chunkLoc.getPort() + ")");

            String chunkFilename = String.format("fileId_%d_chunk%d.enc", 
                                                instructions.getFileId(), i + 1);
            File chunkFile = new File(workingDir, chunkFilename);
            
            downloadChunkFromFS(chunkFile, chunkLoc);
            
            encryptedChunks[i] = chunkFile;
            }

            // Step 2: Verify CRC32 checksums and decrypt chunks.
            SecretKey encryptionKey = encryptionService.getConfiguredKey();
            for (int i = 0; i < 4; i++) {
                DownloadInstructions.ChunkLocation chunkLoc = instructions.getChunks().get(i);
                if (!chunkService.verifyCRC32(encryptedChunks[i], chunkLoc.getCrc32())) {
                    throw new Exception("CRC32 verification failed for chunk " + (i + 1));
                }
                byte[] decryptedData = encryptionService.decrypt(
                        Files.readAllBytes(encryptedChunks[i].toPath()), encryptionKey);
            
            String decryptedFilename = String.format("fileId_%d_chunk%d.tmp", 
                                                    instructions.getFileId(), i + 1);
            File decryptedFile = new File(workingDir, decryptedFilename);
            
            try (FileOutputStream fos = new FileOutputStream(decryptedFile)) {
                fos.write(decryptedData);
            }
            
                decryptedChunks[i] = decryptedFile;
            }

            String outputFilename = String.format("fileId_%d_reassembled.bin", instructions.getFileId());
            reassembledFile = chunkService.reassembleChunks(decryptedChunks, outputFilename, workingDir);
            completed = true;
            return reassembledFile;
        } finally {
            for (File chunk : encryptedChunks) {
                if (chunk != null) chunk.delete();
            }
            for (File chunk : decryptedChunks) {
                if (chunk != null) chunk.delete();
            }
            if (!completed && reassembledFile != null) {
                reassembledFile.delete();
            }
        }
    }

    /**
     * Download a chunk from FS container via SFTP.
     */
    private void downloadChunkFromFS(File localFile, DownloadInstructions.ChunkLocation chunkLoc) throws Exception {
        SftpClient sftpClient = new SftpClient(chunkLoc.getIp(), chunkLoc.getPort(),
                sftpUser, sftpPassword);
        
        try {
            sftpClient.connect();
            sftpClient.downloadFile(localFile.getName(), localFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[DownloadHandler] Failed to download from " + chunkLoc.getFsId() + 
                             ": " + e.getMessage());
            throw e;
        } finally {
            sftpClient.disconnect();
        }
    }
}
