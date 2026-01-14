// src/main/java/com/example/filemanager/service/SessionService.java
package com.example.mainapp.service;

import com.example.mainapp.db.LocalSQLiteDataSource;
import com.example.mainapp.model.User;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;
import java.security.SecureRandom;
import java.util.Base64;

public class SessionService {
    private final LocalSQLiteDataSource dataSource;

    public SessionService() {
        this.dataSource = new LocalSQLiteDataSource();
    }

    /**
     * Create a new session for a user.
     */
    public String createSession(User user) {
        String sessionKey = generateSessionKey();
        String sessionValue = user.getId() + ":" + user.getUsername();
        
        String sql = "INSERT OR REPLACE INTO user_sessions (session_key, user_id, session_value) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionKey);
            ps.setLong(2, user.getId());
            ps.setString(3, sessionValue);
            ps.executeUpdate();
            return sessionKey;
        } catch (SQLException e) {
            throw new RuntimeException("Session creation failed", e);
        }
    }

    /**
     * Retrieve user from session key (for restoring sessions on app restart).
     */
    public Optional<User> getUserBySession(String sessionKey) {
        String sql = "SELECT u.id, u.username, u.password_hash, u.role, u.created_at " +
                     "FROM user_sessions s JOIN users u ON s.user_id = u.id WHERE s.session_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    String username = rs.getString("username");
                    String passwordHash = rs.getString("password_hash");
                    User.Role role = User.Role.valueOf(rs.getString("role"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
                    
                    return Optional.of(new User(id, username, passwordHash, role, createdAt));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            System.err.println("Session lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Invalidate a session (called on logout).
     */
    public void invalidateSession(String sessionKey) {
        String sql = "DELETE FROM user_sessions WHERE session_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Session invalidation failed: " + e.getMessage());
        }
    }

    /**
     * Generate a cryptographically secure session key.
     */
    private String generateSessionKey() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

}
