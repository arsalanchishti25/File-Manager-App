package com.example.filestorage.sftp;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * SFTP Server for file chunk storage and retrieval.
 */
public class SftpServer {

    private final SshServer sshServer;
    private final int port;
    private final String storageBasePath;
    private final String containerId;
    private final String sftpUser;
    private final String sftpPassword;

    public SftpServer(String containerId, int port, String storageBasePath,
                      String sftpUser, String sftpPassword) {
        this.containerId = containerId;
        this.sftpUser = sftpUser;
        this.sftpPassword = sftpPassword;
        this.port = port;
        this.storageBasePath = storageBasePath;
        this.sshServer = SshServer.setUpDefaultServer();
    }

    /**
     * Initialize and start SFTP server.
     */
    public void start() throws IOException {
        System.out.println("[SftpServer] Initializing SFTP server on port " + port);

        // Set port
        sshServer.setPort(port);

        // Set host key provider
        Path hostKeyPath = Paths.get(System.getProperty("java.io.tmpdir"), "sftp_host_key.ser");
        SimpleGeneratorHostKeyProvider keyProvider = new SimpleGeneratorHostKeyProvider(hostKeyPath);
        keyProvider.setAlgorithm("RSA");
        sshServer.setKeyPairProvider(keyProvider);
        // Set password authenticator
        sshServer.setPasswordAuthenticator(new SftpUserInfo(sftpUser, sftpPassword));

        // Set SFTP subsystem factory
        sshServer.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));

        // Create storage directory if not exists
        File storageDir = new File(storageBasePath);
        if (!storageDir.exists()) {
            if (storageDir.mkdirs()) {
                System.out.println("[SftpServer] Created storage directory: " + storageBasePath);
            } else {
                System.err.println("[SftpServer] Failed to create storage directory: " + storageBasePath);
            }
        }
        sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get(storageBasePath)));

        try {
            sshServer.start();
            System.out.println("[SftpServer] ✓ SFTP server started on port " + port);
            System.out.println("[SftpServer] ✓ Storage path: " + storageBasePath);
        } catch (IOException e) {
            System.err.println("[SftpServer] Failed to start SFTP server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Stop SFTP server.
     */
    public void stop() {
        try {
            if (sshServer != null && sshServer.isStarted()) {
                sshServer.stop();
                System.out.println("[SftpServer] SFTP server stopped");
            }
        } catch (IOException e) {
            System.err.println("[SftpServer] Error stopping server: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return sshServer != null && sshServer.isStarted();
    }

    public String getStorageBasePath() {
        return storageBasePath;
    }

    public String getContainerId() {
        return containerId;
    }
}
