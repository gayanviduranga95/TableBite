package com.tablebite.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TableRepository {

    public record RestaurantTable(long id, String tableNumber, String qrToken) {}

    public RestaurantTable findByQrToken(String qrToken) throws SQLException {
        if (qrToken == null || qrToken.isBlank()) {
            return null;
        }
        Long tableId = parseTableId(qrToken);
        String sql = tableId == null
                ? """
                  SELECT table_id, table_number, qr_token
                  FROM restaurant_tables
                  WHERE qr_token = ?
                  """
                : """
                  SELECT table_id, table_number, qr_token
                  FROM restaurant_tables
                  WHERE qr_token = ? OR table_id = ?
                  """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, qrToken);
            if (tableId != null) {
                statement.setLong(2, tableId);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new RestaurantTable(
                        result.getLong("table_id"),
                        result.getString("table_number"),
                        result.getString("qr_token"));
            }
        }
    }

    private Long parseTableId(String value) {
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
