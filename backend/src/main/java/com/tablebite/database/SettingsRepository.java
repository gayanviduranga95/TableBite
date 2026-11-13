package com.tablebite.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Key/value restaurant settings (name, tax and service-charge rates, ...). */
public class SettingsRepository {

    public Map<String, String> findAll() throws SQLException {
        return Jdbc.withConnection(connection -> {
            Map<String, String> settings = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT setting_key, setting_value FROM restaurant_settings ORDER BY setting_key");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    settings.put(result.getString(1), result.getString(2));
                }
            }
            return settings;
        });
    }

    public void saveAll(Map<String, String> values) throws SQLException {
        Jdbc.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO restaurant_settings (setting_key, setting_value, updated_at)
                    VALUES (?, ?, NOW())
                    ON CONFLICT (setting_key)
                    DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_at = NOW()""")) {
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    statement.setString(1, entry.getKey());
                    statement.setString(2, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }
}
