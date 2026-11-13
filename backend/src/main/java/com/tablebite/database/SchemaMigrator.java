package com.tablebite.database;

import com.tablebite.util.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

/**
 * Applies the SQL scripts in {@code resources/database/migration} in order and
 * records each one in {@code tablebite_schema_migrations}.
 *
 * All scripts are idempotent (IF NOT EXISTS / ON CONFLICT), so running them on a
 * database that was created manually or with the Flyway Maven plugin is safe and
 * never removes data. Disable with DB_AUTO_MIGRATE=false.
 */
public final class SchemaMigrator {
    private static final Logger LOG = LoggerUtil.get(SchemaMigrator.class);
    private static final List<String> SCRIPTS = List.of(
            "V1__create_tables.sql",
            "V2__seed_initial_data.sql",
            "V3__restaurant_platform.sql",
            "V4__seed_full_menu.sql");

    private SchemaMigrator() {
    }

    public static void migrate() throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS tablebite_schema_migrations (
                            script     VARCHAR(120) PRIMARY KEY,
                            applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                        )""");
            }
            for (String script : SCRIPTS) {
                if (isApplied(connection, script)) {
                    continue;
                }
                String sql = load(script);
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                    try (PreparedStatement record = connection.prepareStatement(
                            "INSERT INTO tablebite_schema_migrations (script) VALUES (?)")) {
                        record.setString(1, script);
                        record.executeUpdate();
                    }
                    connection.commit();
                    LOG.info("Applied database migration " + script);
                } catch (SQLException exception) {
                    connection.rollback();
                    throw new SQLException("Migration " + script + " failed: "
                            + exception.getMessage(), exception.getSQLState(), exception);
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    private static boolean isApplied(Connection connection, String script) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM tablebite_schema_migrations WHERE script = ?")) {
            statement.setString(1, script);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String load(String script) throws SQLException {
        String path = "/database/migration/" + script;
        try (InputStream input = SchemaMigrator.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new SQLException("Migration script not found on classpath: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SQLException("Could not read " + path, exception);
        }
    }
}
