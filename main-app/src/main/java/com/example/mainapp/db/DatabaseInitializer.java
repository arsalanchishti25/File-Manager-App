package com.example.mainapp.db;

import java.sql.Connection;

public class DatabaseInitializer {

    public static void initializeLocalDatabase() {
        new LocalSQLiteDataSource();
    }

    public static void initializeRemoteDatabase() {
        try (Connection ignored = new RemoteMySQLDataSource().getConnection()) {
            System.out.println("Remote MySQL schema is managed by config/mysql-init.sql.");
        } catch (Exception e) {
            System.err.println("✗ Failed to connect to remote database:");
            e.printStackTrace();
        }
    }
}
