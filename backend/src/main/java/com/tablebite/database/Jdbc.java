package com.tablebite.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;

/** Small JDBC helpers: connection/transaction templates and null-safe getters. */
public final class Jdbc {

    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private Jdbc() {
    }

    public static <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return work.apply(connection);
        }
    }

    /** Runs the work in a single transaction; rolls back on any failure. */
    public static <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public static String iso(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toString();
    }

    public static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    public static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    public static boolean isUniqueViolation(SQLException exception) {
        return "23505".equals(exception.getSQLState());
    }

    public static boolean isForeignKeyViolation(SQLException exception) {
        return "23503".equals(exception.getSQLState());
    }
}
