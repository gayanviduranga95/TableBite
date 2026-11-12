
package com.tablebite.database;

import java.sql.Connection;

public class DatabaseTest {

    public static void main(String[] args) {

        try (Connection connection =
                     DatabaseConfig.getConnection()) {

            System.out.println(
                "SUCCESS: TableBite connected to PostgreSQL!"
            );

            System.out.println(
                "Database: " + connection.getCatalog()
            );

        } catch (Exception e) {

            System.err.println(
                "Database connection failed: " + e.getMessage()
            );
        }
    }
}
