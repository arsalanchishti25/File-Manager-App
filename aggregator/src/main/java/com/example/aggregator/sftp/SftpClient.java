package com.example.aggregator.sftp;

import com.jcraft.jsch.*;
// import java.io.*;

/**
 * SFTP Client for uploading chunks to FS containers.
 */
public class SftpClient {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private Session session;
    private ChannelSftp channelSftp;

    public SftpClient(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
     * Connect to SFTP server.
     */
    public void connect() throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(username, host, port);
        session.setPassword(password);

        // Skip host key verification (for Docker internal network)
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(10000);

        session.connect();

        Channel channel = session.openChannel("sftp");
        channel.connect();
        channelSftp = (ChannelSftp) channel;

        System.out.println("[SftpClient] Connected to " + host + ":" + port);
    }

    /**
     * Disconnect from SFTP server.
     */
    public void disconnect() {
        if (channelSftp != null && channelSftp.isConnected()) {
            channelSftp.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        System.out.println("[SftpClient] Disconnected from " + host);
    }

    /**
     * Upload a file to remote server.
     */
    public void uploadFile(String localFilePath, String remoteFilePath) throws SftpException {
        try {
            channelSftp.put(localFilePath, remoteFilePath);
            System.out.println("[SftpClient] Uploaded: " + localFilePath + " → " + remoteFilePath);
        } catch (SftpException e) {
            System.err.println("[SftpClient] Upload failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Download a file from remote server.
     */
    public void downloadFile(String remoteFilePath, String localFilePath) throws SftpException {
        try {
            channelSftp.get(remoteFilePath, localFilePath);
            System.out.println("[SftpClient] Downloaded: " + remoteFilePath + " → " + localFilePath);
        } catch (SftpException e) {
            System.err.println("[SftpClient] Download failed: " + e.getMessage());
            throw e;
        }
    }

    public boolean isConnected() {
        return channelSftp != null && channelSftp.isConnected();
    }
}
