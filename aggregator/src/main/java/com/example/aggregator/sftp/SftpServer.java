package com.example.aggregator.sftp;

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
 * SFTP Server for receiving files and instructions from Main App.
 */
public class SftpServer {

    private final SshServer sshServer;
    private final int port;
    private final String workingDirectory;
    private final String aggregatorId;
    private final String sftpUser;
    private final String sftpPassword;

    public SftpServer(String aggregatorId, int port, String workingDirectory,
                      String sftpUser, String sftpPassword) {
        this.aggregatorId = aggregatorId;
        this.port = port;
        this.workingDirectory = workingDirectory;
        this.sshServer = SshServer.setUpDefaultServer();
        this.sftpUser = sftpUser;
        this.sftpPassword = sftpPassword;
    }

    /**
     * Initialize and start SFTP server.
     */
    public void start() throws IOException {
        System.out.println("[SftpServer] Initializing SFTP server on port " + port);

        // Set port
        sshServer.setPort(port);

        // Set host key provider
        Path hostKeyPath = Paths.get(System.getProperty("java.io.tmpdir"), 
                                     "sftp_host_key_" + aggregatorId + ".ser");
        SimpleGeneratorHostKeyProvider keyProvider = new SimpleGeneratorHostKeyProvider(hostKeyPath);
        keyProvider.setAlgorithm("RSA");
        sshServer.setKeyPairProvider(keyProvider);

        // Set password authenticator
        sshServer.setPasswordAuthenticator(new SftpUserInfo(sftpUser, sftpPassword));

        // Set SFTP subsystem factory
        sshServer.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));

        // Create working directory if not exists
        File workDir = new File(workingDirectory);
        if (!workDir.exists()) {
            if (workDir.mkdirs()) {
                System.out.println("[SftpServer] Created working directory: " + workingDirectory);
            } else {
                System.err.println("[SftpServer] Failed to create working directory: " + workingDirectory);
            }
        }
        sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get(workingDirectory)));

        try {
            sshServer.start();
            System.out.println("[SftpServer] ✓ SFTP server started on port " + port);
            System.out.println("[SftpServer] ✓ Working directory: " + workingDirectory);
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

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getAggregatorId() {
        return aggregatorId;
    }
}
