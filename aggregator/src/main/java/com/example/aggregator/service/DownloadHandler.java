package com.example.aggregator.service;

import com.example.aggregator.model.DownloadInstructions;
import com.example.aggregator.sftp.SftpClient;
import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

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

    public DownloadHandler(String workingDir) {
        this.workingDir = workingDir;
        this.chunkService = new ChunkService();
        this.encryptionService = new EncryptionService();
    }

    /**
     * Process download operation.
     * Returns the reassembled file.
     */
    public File processDownload(DownloadInstructions instructions) throws Exception {
        System.out.println("\n[DownloadHandler] ═══ Starting Download Process ═══");
        System.out.println("  File ID: " + instructions.getFileId());
        System.out.println("  Filename: " + instructions.getFilename());

        // Step 1: Retrieve encrypted chunks from FS containers
        System.out.println("\n[DownloadHandler] Step 1: Retrieving chunks from FS containers...");
        File[] encryptedChunks = new File[4];
        
        for (int i = 0; i < 4; i++) {
            DownloadInstructions.ChunkLocation chunkLoc = instructions.getChunks().get(i);
            
            System.out.println("  Retrieving chunk " + (i + 1) + " from " + chunkLoc.getFsId() + 
                             " (" + chunkLoc.getIp() + ":" + chunkLoc.getPort() + ")");

            String chunkFilename = String.format("fileId_%d_chunk%d.enc", 
                                                instructions.getFileId(), i + 1);
            File chunkFile = new File(workingDir, chunkFilename);
            
            // TODO: Implement actual SFTP download
            downloadChunkFromFS(chunkFile, chunkLoc);
            
            encryptedChunks[i] = chunkFile;
        }

        // Step 2: Verify CRC32 checksums
        System.out.println("\n[DownloadHandler] Step 2: Verifying checksums...");
        for (int i = 0; i < 4; i++) {
            DownloadInstructions.ChunkLocation chunkLoc = instructions.getChunks().get(i);
            boolean valid = chunkService.verifyCRC32(encryptedChunks[i], chunkLoc.getCrc32());
            
            if (!valid) {
                throw new Exception("CRC32 verification failed for chunk " + (i + 1));
            }
        }

        // Step 3: Decrypt chunks
        System.out.println("\n[DownloadHandler] Step 3: Decrypting chunks...");
        SecretKey encryptionKey = encryptionService.getHardcodedKey();  // TODO: Use proper key management
        File[] decryptedChunks = new File[4];
        
        for (int i = 0; i < 4; i++) {
            byte[] encryptedData = Files.readAllBytes(encryptedChunks[i].toPath());
            byte[] decryptedData = encryptionService.decrypt(encryptedData, encryptionKey);
            
            String decryptedFilename = String.format("fileId_%d_chunk%d.tmp", 
                                                    instructions.getFileId(), i + 1);
            File decryptedFile = new File(workingDir, decryptedFilename);
            
            try (FileOutputStream fos = new FileOutputStream(decryptedFile)) {
                fos.write(decryptedData);
            }
            
            decryptedChunks[i] = decryptedFile;
            System.out.println("  Decrypted chunk " + (i + 1) + ": " + decryptedFile.getName());
        }

        // Step 4: Reassemble file
        System.out.println("\n[DownloadHandler] Step 4: Reassembling file...");
        String outputFilename = String.format("fileId_%d_reassembled.bin", instructions.getFileId());
        File reassembledFile = chunkService.reassembleChunks(decryptedChunks, outputFilename, workingDir);

        // Step 5: Cleanup temporary files
        System.out.println("\n[DownloadHandler] Step 5: Cleaning up temporary files...");
        for (File chunk : encryptedChunks) {
            chunk.delete();
        }
        for (File chunk : decryptedChunks) {
            chunk.delete();
        }

        System.out.println("\n[DownloadHandler] ═══ Download Complete ═══\n");
        return reassembledFile;
    }

    /**
     * Download a chunk from FS container via SFTP.
     * TODO: Implement actual SFTP connection and download
     */
    private void downloadChunkFromFS(File localFile, DownloadInstructions.ChunkLocation chunkLoc) throws Exception {
        SftpClient sftpClient = new SftpClient(chunkLoc.getIp(), chunkLoc.getPort(), "user", "password");
        
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
