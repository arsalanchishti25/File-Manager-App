package com.example.mainapp.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MainAppConfigTest {

    @Test
    void testDefaultsWhenEnvironmentEmpty() {
        MainAppConfig config = new MainAppConfig(Map.of());

        assertEquals("main-app-1", config.getMainAppId());
        assertEquals("main-app-1", config.getServiceId());
        assertEquals("tcp://filemanager-mqtt:1883", config.getMqttBrokerUrl());
        assertEquals("fileapp.db", config.getSqliteDbPath());
        assertEquals("data", config.getAppDataDir());
        assertEquals("filemanager-mysql", config.getMysqlHost());
        assertEquals(3306, config.getMysqlPort());
        assertEquals("filemanager", config.getMysqlDatabase());
        assertEquals("fileapp", config.getMysqlUser());
        assertNull(config.getMysqlPassword());
        assertNull(config.getSftpUser());
        assertNull(config.getSftpPassword());

        assertThrows(IllegalStateException.class, config::requireMysqlPassword);
        assertThrows(IllegalStateException.class, config::requireSftpUser);
        assertThrows(IllegalStateException.class, config::requireSftpPassword);
    }

    @Test
    void testCustomEnvironmentInjection() {
        Map<String, String> env = new HashMap<>();
        env.put("MAIN_APP_ID", "custom-app-42");
        env.put("MQTT_BROKER_URL", "tcp://custom-broker:1883");
        env.put("SQLITE_DB_PATH", "/var/custom/db.sqlite");
        env.put("APP_DATA_DIR", "/var/custom/data");
        env.put("MYSQL_HOST", "db-host");
        env.put("MYSQL_PORT", "3307");
        env.put("MYSQL_DATABASE", "custom_db");
        env.put("MYSQL_USER", "custom_user");
        env.put("MYSQL_PASSWORD", "secret123");
        env.put("SFTP_USER", "sftp_admin");
        env.put("SFTP_PASSWORD", "sftp_secret");

        MainAppConfig config = new MainAppConfig(env);

        assertEquals("custom-app-42", config.getMainAppId());
        assertEquals("tcp://custom-broker:1883", config.getMqttBrokerUrl());
        assertEquals("/var/custom/db.sqlite", config.getSqliteDbPath());
        assertEquals("/var/custom/data", config.getAppDataDir());
        assertEquals("db-host", config.getMysqlHost());
        assertEquals(3307, config.getMysqlPort());
        assertEquals("custom_db", config.getMysqlDatabase());
        assertEquals("custom_user", config.getMysqlUser());
        assertEquals("secret123", config.requireMysqlPassword());
        assertEquals("sftp_admin", config.requireSftpUser());
        assertEquals("sftp_secret", config.requireSftpPassword());
    }

    @Test
    void testServiceIdFallbackWhenMainAppIdMissing() {
        Map<String, String> env = Map.of("SERVICE_ID", "service-node-9");
        MainAppConfig config = new MainAppConfig(env);

        assertEquals("service-node-9", config.getMainAppId());
        assertEquals("service-node-9", config.getServiceId());
    }
}
