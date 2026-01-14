package com.example.mainapp.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class LocalSQLiteDataSource {

    // Later: externalize path to config
    private static final String URL = "jdbc:sqlite:fileapp.db";

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
    
}
