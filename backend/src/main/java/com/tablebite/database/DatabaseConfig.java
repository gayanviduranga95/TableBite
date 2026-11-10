
package com.tablebite.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private static final String URL =
            "jdbc:postgresql://localhost:5432/tablebite_db";

    public static Connection getConnection() throws SQLException {

        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (username == null || password == null) {
            throw new SQLException(
                "DB_USER and DB_PASSWORD must be configured"
            );
        }

        return DriverManager.getConnection(
                URL,
                username,
                password
        );
    }
}
