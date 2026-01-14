// src/main/java/com/example/filemanager/repository/MySQLEventLogRepository.java
package com.example.mainapp.repository;

import com.example.mainapp.db.RemoteMySQLDataSource;
import com.example.mainapp.model.EventLog;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class MySQLEventLogRepository implements EventLogRepository {

    private final RemoteMySQLDataSource dataSource;

    public MySQLEventLogRepository(RemoteMySQLDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<EventLog> findRecentLogs(int limit) {
        String sql =
            "SELECT el.log_id, el.user_id, u.username, el.event_type, " +
            "       el.description, el.timestamp " +
            "FROM event_logs el " +
            "LEFT JOIN users u ON el.user_id = u.id " +
            "ORDER BY el.timestamp DESC " +
            "LIMIT ?";

        List<EventLog> logs = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EventLog log = new EventLog();
                    log.setId(rs.getLong("log_id"));

                    long userId = rs.getLong("user_id");
                    if (rs.wasNull()) {
                        log.setUserId(null);
                    } else {
                        log.setUserId(userId);
                    }

                    log.setUsername(rs.getString("username"));
                    log.setEventType(rs.getString("event_type"));
                    log.setDescription(rs.getString("description"));

                    Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) {
                        LocalDateTime ldt = ts.toInstant()
                                              .atZone(ZoneId.systemDefault())
                                              .toLocalDateTime();
                        log.setTimestamp(ldt);
                    }

                    logs.add(log);
                }
            }

        } catch (SQLException e) {
            System.err.println("MySQLEventLogRepository.findRecentLogs error: " + e.getMessage());
        }

        return logs;
    }

}
