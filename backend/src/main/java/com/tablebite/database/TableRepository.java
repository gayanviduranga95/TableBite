package com.tablebite.database;

import com.tablebite.model.RestaurantTable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TableRepository {

    private static final String SELECT = """
            SELECT t.table_id, t.table_number, t.qr_token, t.seats, t.status, t.location,
                   t.created_at,
                   (SELECT COUNT(*) FROM orders o WHERE o.table_id = t.table_id
                      AND o.status IN ('PENDING','CONFIRMED','PREPARING','READY','SERVED')) AS active_orders
            FROM restaurant_tables t
            WHERE t.deleted_at IS NULL
            """;

    /** Resolves the secure token printed in a table QR code. */
    public RestaurantTable findByQrToken(String qrToken) throws SQLException {
        if (qrToken == null || qrToken.isBlank() || qrToken.length() > 128) {
            return null;
        }
        return single(SELECT + " AND t.qr_token = ?", qrToken);
    }

    /** Manual fallback: resolves a table by its printed number (case-insensitive). */
    public RestaurantTable findByNumber(String tableNumber) throws SQLException {
        if (tableNumber == null || tableNumber.isBlank()) {
            return null;
        }
        return single(SELECT + " AND UPPER(t.table_number) = UPPER(?)", tableNumber.trim());
    }

    public RestaurantTable findById(long id) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT + " AND t.table_id = ?")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? map(result) : null;
                }
            }
        });
    }

    public List<RestaurantTable> findAll() throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT + " ORDER BY length(t.table_number), t.table_number");
                 ResultSet result = statement.executeQuery()) {
                List<RestaurantTable> tables = new ArrayList<>();
                while (result.next()) {
                    tables.add(map(result));
                }
                return tables;
            }
        });
    }

    public boolean numberExists(String tableNumber, Long excludingId) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT 1 FROM restaurant_tables
                    WHERE UPPER(table_number) = UPPER(?) AND table_id <> ?""")) {
                statement.setString(1, tableNumber);
                statement.setLong(2, excludingId == null ? -1 : excludingId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    public RestaurantTable create(String tableNumber, String qrToken, int seats, String status,
                                  String location) throws SQLException {
        long id = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO restaurant_tables (table_number, qr_token, seats, status, location)
                    VALUES (?, ?, ?, ?, ?) RETURNING table_id""")) {
                statement.setString(1, tableNumber);
                statement.setString(2, qrToken);
                statement.setInt(3, seats);
                statement.setString(4, status);
                statement.setString(5, location);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
        return findById(id);
    }

    public RestaurantTable update(long id, String tableNumber, int seats, String status,
                                  String location) throws SQLException {
        int updated = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE restaurant_tables SET table_number = ?, seats = ?, status = ?, location = ?
                    WHERE table_id = ? AND deleted_at IS NULL""")) {
                statement.setString(1, tableNumber);
                statement.setInt(2, seats);
                statement.setString(3, status);
                statement.setString(4, location);
                statement.setLong(5, id);
                return statement.executeUpdate();
            }
        });
        return updated == 0 ? null : findById(id);
    }

    public void setStatus(long id, String status) throws SQLException {
        Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE restaurant_tables SET status = ? WHERE table_id = ?")) {
                statement.setString(1, status);
                statement.setLong(2, id);
                return statement.executeUpdate();
            }
        });
    }

    public RestaurantTable updateToken(long id, String qrToken) throws SQLException {
        int updated = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE restaurant_tables SET qr_token = ? WHERE table_id = ? AND deleted_at IS NULL")) {
                statement.setString(1, qrToken);
                statement.setLong(2, id);
                return statement.executeUpdate();
            }
        });
        return updated == 0 ? null : findById(id);
    }

    /**
     * Deletes a table. Tables with order history are archived (soft-deleted, number
     * released, QR token invalidated) so past orders keep their table reference.
     */
    public boolean delete(long id, String archivedToken) throws SQLException {
        return Jdbc.inTransaction(connection -> {
            boolean hasOrders;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM orders WHERE table_id = ? LIMIT 1")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    hasOrders = result.next();
                }
            }
            String sql = hasOrders
                    ? """
                      UPDATE restaurant_tables
                      SET deleted_at = NOW(), status = 'OUT_OF_SERVICE', qr_token = ?,
                          table_number = LEFT(table_number, 8) || '~' || table_id
                      WHERE table_id = ? AND deleted_at IS NULL"""
                    : "DELETE FROM restaurant_tables WHERE table_id = ? AND deleted_at IS NULL";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (hasOrders) {
                    statement.setString(1, archivedToken);
                    statement.setLong(2, id);
                } else {
                    statement.setLong(1, id);
                }
                return statement.executeUpdate() > 0;
            }
        });
    }

    private RestaurantTable single(String sql, String value) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? map(result) : null;
                }
            }
        });
    }

    private RestaurantTable map(ResultSet result) throws SQLException {
        return new RestaurantTable(
                result.getLong("table_id"),
                result.getString("table_number"),
                result.getString("qr_token"),
                result.getInt("seats"),
                result.getString("status"),
                result.getString("location"),
                result.getInt("active_orders"),
                Jdbc.iso(result, "created_at"));
    }
}
