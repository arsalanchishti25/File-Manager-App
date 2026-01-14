package com.example.aggregator.service;

import java.io.*;
// import java.nio.file.*;
import java.util.zip.CRC32;

/**
 * Handles file chunking and reassembly operations.
 */
public class ChunkService {

    /**
     * Split a file into 4 equal chunks.
     * Returns an array of chunk file paths.
     */
    public File[] splitFile(File originalFile, String workingDir) throws IOException {
        long fileSize = originalFile.length();
        long chunkSize = (fileSize + 3) / 4;  // Divide by 4, round up

        System.out.println("[ChunkService] Splitting file: " + originalFile.getName());
        System.out.println("  Total size: " + fileSize + " bytes");
        System.out.println("  Chunk size: " + chunkSize + " bytes");

        File[] chunkFiles = new File[4];

        try (FileInputStream fis = new FileInputStream(originalFile)) {
            for (int i = 0; i < 4; i++) {
                String chunkFilename = originalFile.getName().replace(".bin", "") + "_chunk" + (i + 1) + ".tmp";
                File chunkFile = new File(workingDir, chunkFilename);
                chunkFiles[i] = chunkFile;

                // Calculate actual chunk size (last chunk might be smaller)
                long actualChunkSize = (i == 3) ? (fileSize - (chunkSize * 3)) : chunkSize;

                try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
                    byte[] buffer = new byte[8192];
                    long bytesRemaining = actualChunkSize;
                    int bytesRead;

                    while (bytesRemaining > 0 && (bytesRead = fis.read(buffer, 0, 
                            (int) Math.min(buffer.length, bytesRemaining))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        bytesRemaining -= bytesRead;
                    }
                }

                System.out.println("  Chunk " + (i + 1) + ": " + chunkFile.getName() + 
                                 " (" + actualChunkSize + " bytes)");
            }
        }

        return chunkFiles;
    }

    /**
     * Reassemble 4 chunks into a single file.
     */
    public File reassembleChunks(File[] chunkFiles, String outputFilename, String workingDir) throws IOException {
        System.out.println("[ChunkService] Reassembling " + chunkFiles.length + " chunks");

        File outputFile = new File(workingDir, outputFilename);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (int i = 0; i < chunkFiles.length; i++) {
                File chunkFile = chunkFiles[i];
                
                if (!chunkFile.exists()) {
                    throw new FileNotFoundException("Chunk not found: " + chunkFile.getName());
                }

                System.out.println("  Reassembling chunk " + (i + 1) + ": " + chunkFile.getName());

                try (FileInputStream fis = new FileInputStream(chunkFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }
        }

        System.out.println("[ChunkService] ✓ Reassembled file: " + outputFile.getName() + 
                         " (" + outputFile.length() + " bytes)");

        return outputFile;
    }

    /**
     * Calculate CRC32 checksum for a file.
     */
    public String calculateCRC32(File file) throws IOException {
        CRC32 crc32 = new CRC32();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                crc32.update(buffer, 0, bytesRead);
            }
        }

        String checksum = Long.toHexString(crc32.getValue());
        System.out.println("[ChunkService] CRC32 for " + file.getName() + ": " + checksum);

        return checksum;
    }

    /**
     * Verify CRC32 checksum of a file.
     */
    public boolean verifyCRC32(File file, String expectedCRC32) throws IOException {
        String actualCRC32 = calculateCRC32(file);
        boolean matches = actualCRC32.equalsIgnoreCase(expectedCRC32);

        if (matches) {
            System.out.println("[ChunkService] ✓ CRC32 verification passed for " + file.getName());
        } else {
            System.err.println("[ChunkService] ✗ CRC32 verification failed for " + file.getName());
            System.err.println("  Expected: " + expectedCRC32);
            System.err.println("  Actual: " + actualCRC32);
        }

        return matches;
    }
}
