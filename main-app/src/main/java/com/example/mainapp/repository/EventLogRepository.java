package com.example.mainapp.repository;

import com.example.mainapp.model.EventLog;

import java.util.List;

public interface EventLogRepository {

    List<EventLog> findRecentLogs(int limit);
}
