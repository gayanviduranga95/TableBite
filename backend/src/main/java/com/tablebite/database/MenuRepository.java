package com.tablebite.database;

import com.tablebite.model.MenuItem;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MenuRepository {

    /** Validated values for creating or updating a dish. */
    public record MenuItemInput(String name, String description, BigDecimal price,
                                Long categoryId, String imageUrl, boolean available,
                                boolean popular, boolean recommended, boolean vegetarian,
                                boolean spicy, int prepMinutes) {
    }

    private static final String SELECT = """
            SELECT m.menu_item_id, m.item_name, m.description, m.price, m.category_id,
                   c.category_name, m.image_url, m.available, m.is_popular, m.is_recommended,
                   m.is_vegetarian, m.is_spicy, m.prep_minutes, m.created_at,
                   (m.created_at > NOW() - INTERVAL '14 days') AS is_new,
                   COALESCE(s.qty, 0) AS order_count
            FROM menu_items m
            LEFT JOIN categories c ON c.category_id = m.category_id
            LEFT JOIN (SELECT oi.menu_item_id, SUM(oi.quantity) AS qty
                       FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
                       WHERE o.status <> 'CANCELLED'
                       GROUP BY oi.menu_item_id) s ON s.menu_item_id = m.menu_item_id
            WHERE m.deleted_at IS NULL
            """;

    private static final String ORDER_BY =
            " ORDER BY c.sort_order NULLS LAST, c.category_name NULLS LAST, m.item_name";

    /** Available dishes only (used by the TCP protocol GET_MENU command). */
    public List<MenuItem> findAvailable() throws SQLException {
        return query(SELECT + " AND m.available AND (c.active IS NULL OR c.active)" + ORDER_BY);
    }

    /**
     * Customer menu: every live dish in an active category, including sold-out ones so the
     * UI can show an availability indicator.
     */
    public List<MenuItem> findCustomerMenu() throws SQLException {
        return query(SELECT + " AND (c.active IS NULL OR c.active)" + ORDER_BY);
    }

    /** Admin view: all non-deleted dishes. */
    public List<MenuItem> findAll() throws SQLException {
        return query(SELECT + ORDER_BY);
    }

    public MenuItem findById(long id) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT + " AND m.menu_item_id = ?")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? map(result) : null;
                }
            }
        });
    }

    public MenuItem create(MenuItemInput input) throws SQLException {
        long id = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO menu_items (item_name, description, price, category_id, image_url,
                        available, is_popular, is_recommended, is_vegetarian, is_spicy, prep_minutes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING menu_item_id""")) {
                bind(statement, input);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
        return findById(id);
    }

    public MenuItem update(long id, MenuItemInput input) throws SQLException {
        int updated = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE menu_items SET item_name = ?, description = ?, price = ?, category_id = ?,
                        image_url = ?, available = ?, is_popular = ?, is_recommended = ?,
                        is_vegetarian = ?, is_spicy = ?, prep_minutes = ?, updated_at = NOW()
                    WHERE menu_item_id = ? AND deleted_at IS NULL""")) {
                bind(statement, input);
                statement.setLong(12, id);
                return statement.executeUpdate();
            }
        });
        return updated == 0 ? null : findById(id);
    }

    public MenuItem setAvailability(long id, boolean available) throws SQLException {
        int updated = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE menu_items SET available = ?, updated_at = NOW()
                    WHERE menu_item_id = ? AND deleted_at IS NULL""")) {
                statement.setBoolean(1, available);
                statement.setLong(2, id);
                return statement.executeUpdate();
            }
        });
        return updated == 0 ? null : findById(id);
    }

    /**
     * Soft-deletes a dish so historical orders that reference it stay intact.
     */
    public boolean delete(long id) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE menu_items SET deleted_at = NOW(), available = FALSE, updated_at = NOW()
                    WHERE menu_item_id = ? AND deleted_at IS NULL""")) {
                statement.setLong(1, id);
                return statement.executeUpdate() > 0;
            }
        });
    }

    private List<MenuItem> query(String sql) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {
                List<MenuItem> items = new ArrayList<>();
                while (result.next()) {
                    items.add(map(result));
                }
                return items;
            }
        });
    }

    private void bind(PreparedStatement statement, MenuItemInput input) throws SQLException {
        statement.setString(1, input.name());
        statement.setString(2, input.description());
        statement.setBigDecimal(3, input.price());
        Jdbc.setNullableLong(statement, 4, input.categoryId());
        statement.setString(5, input.imageUrl());
        statement.setBoolean(6, input.available());
        statement.setBoolean(7, input.popular());
        statement.setBoolean(8, input.recommended());
        statement.setBoolean(9, input.vegetarian());
        statement.setBoolean(10, input.spicy());
        statement.setInt(11, input.prepMinutes());
    }

    private MenuItem map(ResultSet result) throws SQLException {
        return new MenuItem(
                result.getLong("menu_item_id"),
                result.getString("item_name"),
                result.getString("description"),
                result.getBigDecimal("price"),
                Jdbc.nullableLong(result, "category_id"),
                result.getString("category_name"),
                result.getString("image_url"),
                result.getBoolean("available"),
                result.getBoolean("is_popular"),
                result.getBoolean("is_recommended"),
                result.getBoolean("is_vegetarian"),
                result.getBoolean("is_spicy"),
                result.getInt("prep_minutes"),
                Jdbc.iso(result, "created_at"),
                result.getBoolean("is_new"),
                result.getInt("order_count"));
    }
}
