package com.example.filestorage.sftp;

import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

/**
 * Simple password authenticator for SFTP.
 * Currently accepts any username/password.
 * TODO: Implement proper authentication
 */
public class SftpUserInfo implements PasswordAuthenticator {

    @Override
    public boolean authenticate(String username, String password, ServerSession session) {
        // TODO: Implement actual authentication against Main App database
        // For now, accept any credentials
        System.out.println("[SftpUserInfo] Authentication attempt: " + username);
        return true;
    }
}
