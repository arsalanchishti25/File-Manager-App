package com.example.mainapp.config;

import java.util.Collections;
import java.util.Map;
import java.lang.System;
import java.nio.file.Path;

/**
 * Centralized configuration for main-app.
 * Supports constructor injection of environment maps for deterministic unit testing.
 * Does not embed silent credential fallbacks in source code.
 */
public class MainAppConfig {

    private final String serviceId;
    private final String mainAppId;
    private final String mqttBrokerUrl;
    private final String sqliteDbPath;
    private final String appDataDir;

    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUser;
    private final String mysqlPassword;

    private final String sftpUser;
    private final String sftpPassword;

    private static volatile MainAppConfig instance;

    public MainAppConfig(Map<String, String> env) {
        Map<String, String> source = env != null ? env : Collections.emptyMap();

        // Service & App IDs
        String configuredMainAppId = source.get("MAIN_APP_ID");
        if (configuredMainAppId == null || configuredMainAppId.isBlank()) {
            configuredMainAppId = source.get("SERVICE_ID");
        }
        this.mainAppId = (configuredMainAppId != null && !configuredMainAppId.isBlank())
                ? configuredMainAppId.trim()
                : "main-app-1";
        this.serviceId = this.mainAppId;

        // MQTT
        String brokerUrl = source.get("MQTT_BROKER_URL");
        this.mqttBrokerUrl = (brokerUrl != null && !brokerUrl.isBlank())
                ? brokerUrl.trim()
                : "tcp://filemanager-mqtt:1883";

        // SQLite & Paths
        String sqlitePath = source.get("SQLITE_DB_PATH");
        if (sqlitePath == null || sqlitePath.isBlank()) {
            sqlitePath = System.getProperty("sqlite.db.path");
        }
        this.sqliteDbPath = (sqlitePath != null && !sqlitePath.isBlank())
                ? sqlitePath.trim()
                : Path.of(System.getProperty("user.home"), "file-manager-data", "fileapp.db").toString();

        String dataDir = source.get("APP_DATA_DIR");
        this.appDataDir = (dataDir != null && !dataDir.isBlank())
                ? dataDir.trim()
                : "data";

        // MySQL Metadata
        String host = source.get("MYSQL_HOST");
        this.mysqlHost = (host != null && !host.isBlank()) ? host.trim() : "filemanager-mysql";

        String portStr = source.get("MYSQL_PORT");
        int parsedPort = 3306;
        if (portStr != null && !portStr.isBlank()) {
            try {
                parsedPort = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                parsedPort = 3306;
            }
        }
        this.mysqlPort = parsedPort;

        String db = source.get("MYSQL_DATABASE");
        this.mysqlDatabase = (db != null && !db.isBlank()) ? db.trim() : "filemanager";

        String user = source.get("MYSQL_USER");
        this.mysqlUser = (user != null && !user.isBlank()) ? user.trim() : "fileapp";

        // Credentials - Never supply a silent hardcoded password fallback in code
        this.mysqlPassword = source.get("MYSQL_PASSWORD");

        // SFTP Credentials
        this.sftpUser = source.get("SFTP_USER");
        this.sftpPassword = source.get("SFTP_PASSWORD");
    }

    public static MainAppConfig fromEnv() {
        return new MainAppConfig(System.getenv());
    }

    public static MainAppConfig fromMap(Map<String, String> env) {
        return new MainAppConfig(env);
    }

    public static MainAppConfig getInstance() {
        if (instance == null) {
            synchronized (MainAppConfig.class) {
                if (instance == null) {
                    instance = fromEnv();
                }
            }
        }
        return instance;
    }

    public static void setInstance(MainAppConfig customInstance) {
        instance = customInstance;
    }

    public static void resetInstance() {
        instance = null;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getMainAppId() {
        return mainAppId;
    }

    public String getMqttBrokerUrl() {
        return mqttBrokerUrl;
    }

    public String getSqliteDbPath() {
        return sqliteDbPath;
    }

    public String getAppDataDir() {
        return appDataDir;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUser() {
        return mysqlUser;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public String requireMysqlPassword() {
        if (mysqlPassword == null || mysqlPassword.isBlank()) {
            throw new IllegalStateException("Missing required configuration: MYSQL_PASSWORD");
        }
        return mysqlPassword;
    }

    public String getSftpUser() {
        return sftpUser;
    }

    public String requireSftpUser() {
        if (sftpUser == null || sftpUser.isBlank()) {
            throw new IllegalStateException("Missing required configuration: SFTP_USER");
        }
        return sftpUser;
    }

    public String getSftpPassword() {
        return sftpPassword;
    }

    public String requireSftpPassword() {
        if (sftpPassword == null || sftpPassword.isBlank()) {
            throw new IllegalStateException("Missing required configuration: SFTP_PASSWORD");
        }
        return sftpPassword;
    }
}
