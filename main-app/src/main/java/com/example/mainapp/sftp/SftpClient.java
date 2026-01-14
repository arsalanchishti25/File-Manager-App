package com.example.mainapp.sftp;

import com.jcraft.jsch.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * SFTP Client for Main App.
 * Handles file transfers to/from Aggregator and FS containers.
 */
public class SftpClient {

    private final String clientId;
    private JSch jsch;
    private Session session;
    private ChannelSftp channelSftp;

    public SftpClient(String clientId) {
        this.clientId = clientId;
        this.jsch = new JSch();
    }

    /**
     * Connect to an SFTP server.
     * 
     * @param host SFTP server IP/hostname
     * @param port SFTP port
     * @param username Username (default: "sftpuser")
     * @param password Password (default: empty for key-based auth)
     * @throws JSchException if connection fails
     */
    public void connect(String host, int port, String username, String password) throws JSchException {
        try {
            session = jsch.getSession(username, host, port);
            
            if (password != null && !password.isEmpty()) {
                session.setPassword(password);
            }
            
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            session.connect(10000); // 10s timeout

            Channel channel = session.openChannel("sftp");
            channel.connect(10000);
            
            channelSftp = (ChannelSftp) channel;
            
            System.out.println("[SftpClient-" + clientId + "] Connected to SFTP: " + host + ":" + port);

        } catch (JSchException e) {
            System.err.println("[SftpClient-" + clientId + "] SFTP connection failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Disconnect from SFTP server.
     */
    public void disconnect() {
        try {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            System.out.println("[SftpClient-" + clientId + "] Disconnected from SFTP");
        } catch (Exception e) {
            System.err.println("[SftpClient-" + clientId + "] Disconnect error: " + e.getMessage());
        }
    }

    /**
     * Upload a local file to remote SFTP server.
     * 
     * @param localPath Local file path
     * @param remoteFileName Remote filename (no path, goes to home dir)
     * @throws SftpException if upload fails
     */
    public void uploadFile(Path localPath, String remoteFileName) throws SftpException {
        if (!channelSftp.isConnected()) {
            throw new SftpException(ChannelSftp.SSH_FX_FAILURE, "SFTP channel not connected");
        }

        try {
            channelSftp.put(localPath.toString(), remoteFileName);
            System.out.println("[SftpClient-" + clientId + "] Uploaded: " + localPath.getFileName() 
                             + " -> " + remoteFileName);
        } catch (SftpException e) {
            System.err.println("[SftpClient-" + clientId + "] Upload failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Download a file from remote SFTP server.
     * 
     * @param remoteFileName Remote filename
     * @param localPath Local destination path
     * @throws SftpException if download fails
     */
    public void downloadFile(String remoteFileName, Path localPath) throws SftpException {
        if (!channelSftp.isConnected()) {
            throw new SftpException(ChannelSftp.SSH_FX_FAILURE, "SFTP channel not connected");
        }

        try {
            channelSftp.get(remoteFileName, localPath.toString());
            System.out.println("[SftpClient-" + clientId + "] Downloaded: " + remoteFileName 
                             + " -> " + localPath.getFileName());
        } catch (SftpException e) {
            System.err.println("[SftpClient-" + clientId + "] Download failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * List files in remote directory.
     */
    public List<ChannelSftp.LsEntry> listFiles(String remotePath) throws SftpException {
        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> vector = channelSftp.ls(remotePath);
            return new ArrayList<ChannelSftp.LsEntry>(vector);
        } catch (SftpException e) {
            System.err.println("[SftpClient-" + clientId + "] List failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Delete a file on remote server.
     */
    public void deleteFile(String remoteFileName) throws SftpException {
        try {
            channelSftp.rm(remoteFileName);
            System.out.println("[SftpClient-" + clientId + "] Deleted: " + remoteFileName);
        } catch (SftpException e) {
            System.err.println("[SftpClient-" + clientId + "] Delete failed: " + e.getMessage());
            throw e;
        }
    }

    public boolean isConnected() {
        return channelSftp != null && channelSftp.isConnected();
    }
}
