// package com.example.mainapp.db;

// import java.sql.Connection;
// import java.sql.DriverManager;
// import java.sql.SQLException;

// public class RemoteMySQLDataSource {

//     // placeholders; later move to config + env vars
//     private static final String URL = "jdbc:mysql://localhost:3306/fileapp"; //change url to match mysql container
//     private static final String USER = "root";
//     private static final String PASSWORD = "MySQL$Password1";
  
//     public Connection getConnection() throws SQLException {
//         return DriverManager.getConnection(URL, USER, PASSWORD);
//     }
// }
package com.example.mainapp.db;

import java.sql.*;

public class RemoteMySQLDataSource {
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    
    // Environment-aware configuration
    private final String host;
    private final String port;
    private final String database;
    private final String user;
    private final String password;
    private final String url;
    
    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found", e);
        }
    }
    
    // No-arg constructor reads from environment variables
    public RemoteMySQLDataSource() {
        this.host = System.getenv("MYSQL_HOST") != null 
            ? System.getenv("MYSQL_HOST") 
            : "filemanager-mysql";
        
        this.port = System.getenv("MYSQL_PORT") != null 
            ? System.getenv("MYSQL_PORT") 
            : "3306";
        
        this.database = System.getenv("MYSQL_DATABASE") != null 
            ? System.getenv("MYSQL_DATABASE") 
            : "filemanager";
        
        this.user = System.getenv("MYSQL_USER") != null 
            ? System.getenv("MYSQL_USER") 
            : "fileapp";
        
        this.password = System.getenv("MYSQL_PASSWORD") != null 
            ? System.getenv("MYSQL_PASSWORD") 
            : "fileapp123";
        
        this.url = String.format(
            "jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            host, port, database
        );
        
        System.out.println("Initialized MySQL datasource: " + this.url);
    }
    
    // Instance method - matches your existing repository code
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
    
    // Test method
    public static void main(String[] args) {
        try {
            RemoteMySQLDataSource ds = new RemoteMySQLDataSource();
            Connection conn = ds.getConnection();
            System.out.println("✓ MySQL connection successful!");
            conn.close();
        } catch (SQLException e) {
            System.err.println("✗ MySQL connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
