package com.tablebite.database;

import com.tablebite.model.Category;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoryRepository {

    public List<Category> findAll(boolean includeInactive) throws SQLException {
        String sql = """
                SELECT c.category_id, c.category_name, c.icon, c.sort_order, c.active,
                       (SELECT COUNT(*) FROM menu_items m
                         WHERE m.category_id = c.category_id AND m.deleted_at IS NULL) AS item_count
                FROM categories c
                """ + (includeInactive ? "" : " WHERE c.active ")
                + " ORDER BY c.sort_order, c.category_name";
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {
                List<Category> categories = new ArrayList<>();
                while (result.next()) {
                    categories.add(map(result));
                }
                return categories;
            }
        });
    }

    public Category findById(long id) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT c.category_id, c.category_name, c.icon, c.sort_order, c.active,
                           (SELECT COUNT(*) FROM menu_items m
                             WHERE m.category_id = c.category_id AND m.deleted_at IS NULL) AS item_count
                    FROM categories c WHERE c.category_id = ?""")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? map(result) : null;
                }
            }
        });
    }

    public Category create(String name, String icon, int sortOrder, boolean active)
            throws SQLException {
        long id = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO categories (category_name, icon, sort_order, active)
                    VALUES (?, ?, ?, ?) RETURNING category_id""")) {
                statement.setString(1, name);
                statement.setString(2, icon);
                statement.setInt(3, sortOrder);
                statement.setBoolean(4, active);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
        return findById(id);
    }

    public Category update(long id, String name, String icon, int sortOrder, boolean active)
            throws SQLException {
        int updated = Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE categories SET category_name = ?, icon = ?, sort_order = ?, active = ?
                    WHERE category_id = ?""")) {
                statement.setString(1, name);
                statement.setString(2, icon);
                statement.setInt(3, sortOrder);
                statement.setBoolean(4, active);
                statement.setLong(5, id);
                return statement.executeUpdate();
            }
        });
        return updated == 0 ? null : findById(id);
    }

    /** Deletes a category; returns false when it does not exist. */
    public boolean delete(long id) throws SQLException {
        return Jdbc.inTransaction(connection -> {
            // Detach soft-deleted dishes so the FK does not block the delete.
            try (PreparedStatement detach = connection.prepareStatement(
                    "UPDATE menu_items SET category_id = NULL WHERE category_id = ? AND deleted_at IS NOT NULL")) {
                detach.setLong(1, id);
                detach.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM categories WHERE category_id = ?")) {
                statement.setLong(1, id);
                return statement.executeUpdate() > 0;
            }
        });
    }

    private Category map(ResultSet result) throws SQLException {
        return new Category(
                result.getLong("category_id"),
                result.getString("category_name"),
                result.getString("icon"),
                result.getInt("sort_order"),
                result.getBoolean("active"),
                result.getInt("item_count"));
    }
}
