package com.example.mainapp.service;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.logging.MysqlEventLogger;

import java.sql.Connection;
import java.sql.SQLException;

public class ConnectivityService {

    private final RemoteMySQLDataSource dataSource;

    // Cached state
    private volatile boolean lastKnownOnline = true;
    private volatile long lastCheckMillis = 0L;

    // How often we re-check (e.g. every 5 seconds)
    private static final long CHECK_INTERVAL_MS = 5000;

    public ConnectivityService() {
        this.dataSource = new RemoteMySQLDataSource();
    }

    public ConnectivityService(RemoteMySQLDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Main entry point: cheap "are we online?" check.
     */
    public boolean isOnline() {
        long now = System.currentTimeMillis();

        // Use cached value if check is recent
        if (now - lastCheckMillis < CHECK_INTERVAL_MS) {
            return lastKnownOnline;
        }

        // Otherwise, try a lightweight ping
        try (Connection conn = dataSource.getConnection()) {
            // Use driver-level validation if available
            boolean valid = conn.isValid(2); // 2 seconds timeout
            updateState(valid);
            return valid;
        } catch (SQLException e) {
            updateState(false);
            return false;
        }
    }

    /**
     * Call this from catch blocks handling SQLExceptions
     * that look like connection problems.
     */
    public void markOfflineOnSQLException(SQLException e) {
        // Very simple heuristic for now: any SQL exception here means "probably offline".
        // You could inspect SQL state or error codes to be more precise.
        updateState(false);
        // Best-effort log to MySQL; if this also fails, just print to stderr
        try {
            new MysqlEventLogger(new RemoteMySQLDataSource())
                .logEvent(
                    null,
                    "CONNECTIVITY_OFFLINE",
                    "Marked offline due to SQL error: " + e.getMessage()
                );
        } catch (Exception ex) {
            System.err.println("Failed to log CONNECTIVITY_OFFLINE event: " + ex.getMessage());
        }
    }

    private void updateState(boolean online) {
        lastKnownOnline = online;
        lastCheckMillis = System.currentTimeMillis();
    }
}
