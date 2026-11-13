package com.tablebite.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC connection settings, read from environment variables so that no
 * credentials are hard-coded:
 * <ul>
 *   <li>DB_URL  (optional, default jdbc:postgresql://DB_HOST:DB_PORT/DB_NAME)</li>
 *   <li>DB_HOST (default localhost), DB_PORT (default 5432), DB_NAME (default tablebite_db)</li>
 *   <li>DB_USER, DB_PASSWORD (required)</li>
 * </ul>
 */
public class DatabaseConfig {

    private static final String URL = resolveUrl();

    private static String resolveUrl() {
        String url = System.getenv("DB_URL");
        if (url != null && !url.isBlank()) {
            return url;
        }
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String name = System.getenv().getOrDefault("DB_NAME", "tablebite_db");
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    public static String url() {
        return URL;
    }

    public static Connection getConnection() throws SQLException {
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (username == null || password == null) {
            throw new SQLException("DB_USER and DB_PASSWORD must be configured");
        }
        return DriverManager.getConnection(URL, username, password);
    }
}
