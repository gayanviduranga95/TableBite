package com.tablebite.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MenuRepository {

    public record MenuItem(long id, String name, String description,
                           BigDecimal price, String category) {}

    public List<MenuItem> findAvailable() throws SQLException {
        String sql = """
                SELECT menu_item_id, item_name, description, price, category_id
                FROM menu_items
                WHERE available = TRUE
                ORDER BY category_id NULLS LAST, item_name
                """;
        List<MenuItem> items = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Object categoryId = result.getObject("category_id");
                items.add(new MenuItem(
                        result.getLong("menu_item_id"),
                        result.getString("item_name"),
                        result.getString("description"),
                        result.getBigDecimal("price"),
                        categoryId == null ? null : categoryId.toString()));
            }
        }
        return items;
    }
}
