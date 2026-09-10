package com.example.aggregator.sftp;

import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

/**
 * Simple password authenticator for SFTP.
 * Currently accepts any username/password.
 * TODO: Implement proper authentication
 */
public class SftpUserInfo implements PasswordAuthenticator {
    private final String configuredUsername;
    private final String configuredPassword;

    public SftpUserInfo(String configuredUsername, String configuredPassword) {
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public boolean authenticate(String username, String password, ServerSession session) {
        return configuredUsername.equals(username) && configuredPassword.equals(password);
    }
}
