package com.example.mainapp.service;

import com.example.mainapp.model.User;
import java.util.Optional;

/**
 * Thread-safe session manager for the current logged-in user.
 * Replaces the flawed Singleton pattern with a cleaner, testable design.
 */
public class SessionManager {
    private static final SessionManager instance = new SessionManager();
    
    private String currentSessionKey;
    private User currentUser;
    private final SessionService sessionService;

    private SessionManager() {
        this.sessionService = new SessionService();
    }

    public static SessionManager getInstance() {
        return instance;
    }

    /**
     * Login: Create a session and store the user locally.
     */
    public synchronized void login(User user) {
        String sessionKey = sessionService.createSession(user);
        this.currentSessionKey = sessionKey;
        this.currentUser = user;
        System.out.println("✓ Session created for user: " + user.getUsername());
    }

    /**
     * Get the currently logged-in user.
     */
    public synchronized Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null && currentSessionKey != null;
    }

    /**
     * Get the current session key (useful for multi-window scenarios).
     */
    public synchronized Optional<String> getSessionKey() {
        return Optional.ofNullable(currentSessionKey);
    }

    
    public synchronized void logout() {
        if (currentSessionKey != null) {
            sessionService.invalidateSession(currentSessionKey);
        }
        this.currentSessionKey = null;
        this.currentUser = null;
        System.out.println("✓ User logged out, session invalidated.");
    }

    /**
     * Restore session from key (useful for app restart).
     */
    public synchronized boolean restoreSession(String sessionKey) {
        Optional<User> userOpt = sessionService.getUserBySession(sessionKey);
        if (userOpt.isPresent()) {
            this.currentSessionKey = sessionKey;
            this.currentUser = userOpt.get();
            return true;
        }
        return false;
    }

}