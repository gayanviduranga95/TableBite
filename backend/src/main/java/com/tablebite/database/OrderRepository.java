package com.tablebite.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OrderRepository {
    public record OrderItemInput(long menuItemId, int quantity) {}
    public record OrderSummary(long id, long tableId, String status,
                               String createdAt) {}
    public record OrderItem(long menuItemId, String name, int quantity,
                            BigDecimal unitPrice) {}
    public record OrderDetails(long id, long tableId, String status,
                               String createdAt, List<OrderItem> items) {}

    public long createOrder(long tableId, List<OrderItemInput> items)
            throws SQLException {
        if (tableId <= 0) {
            throw new IllegalArgumentException("Table ID must be positive");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }
        for (OrderItemInput item : items) {
            if (item == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Order quantities must be positive");
            }
        }

        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long orderId;
                String orderSql = """
                        INSERT INTO orders (table_id, status)
                        VALUES (?, 'PENDING')
                        RETURNING order_id
                        """;
                try (PreparedStatement statement = connection.prepareStatement(orderSql)) {
                    statement.setLong(1, tableId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Order ID was not returned");
                        }
                        orderId = result.getLong("order_id");
                    }
                }

                String priceSql = """
                        SELECT price FROM menu_items
                        WHERE menu_item_id = ? AND available = TRUE
                        """;
                String itemSql = """
                        INSERT INTO order_items
                            (order_id, menu_item_id, quantity, unit_price)
                        VALUES (?, ?, ?, ?)
                        """;
                try (PreparedStatement priceStatement = connection.prepareStatement(priceSql);
                     PreparedStatement itemStatement = connection.prepareStatement(itemSql)) {
                    for (OrderItemInput item : items) {
                        priceStatement.setLong(1, item.menuItemId());
                        BigDecimal price;
                        try (ResultSet result = priceStatement.executeQuery()) {
                            if (!result.next()) {
                                throw new IllegalArgumentException(
                                        "Menu item not found or unavailable");
                            }
                            price = result.getBigDecimal("price");
                        }
                        itemStatement.setLong(1, orderId);
                        itemStatement.setLong(2, item.menuItemId());
                        itemStatement.setInt(3, item.quantity());
                        itemStatement.setBigDecimal(4, price);
                        itemStatement.executeUpdate();
                    }
                }
                connection.commit();
                return orderId;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw (RuntimeException) exception;
            }
        }
    }

    public void updateStatus(long orderId, String status) throws SQLException {
        if (orderId <= 0 || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Invalid order status update");
        }
        String normalizedStatus = status.toUpperCase();
        if (!List.of("PENDING", "ACCEPTED", "PREPARING", "READY", "SERVED", "CANCELLED")
                .contains(normalizedStatus)) {
            throw new IllegalArgumentException("Unsupported order status");
        }
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String currentStatus;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT status FROM orders WHERE order_id = ? FOR UPDATE")) {
                    statement.setLong(1, orderId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Order not found");
                        }
                        currentStatus = result.getString("status");
                    }
                }
                if (!isValidTransition(currentStatus, normalizedStatus)) {
                    throw new IllegalArgumentException(
                            "Cannot change order from " + currentStatus + " to " + normalizedStatus);
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE orders SET status = ? WHERE order_id = ?")) {
                    statement.setString(1, normalizedStatus);
                    statement.setLong(2, orderId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw (RuntimeException) exception;
            }
        }
    }

    private boolean isValidTransition(String current, String next) {
        if ("CANCELLED".equals(current) || "SERVED".equals(current)) {
            return false;
        }
        if ("CANCELLED".equals(next)) {
            return "PENDING".equals(current) || "ACCEPTED".equals(current);
        }
        return switch (current) {
            case "PENDING" -> "ACCEPTED".equals(next);
            case "ACCEPTED" -> "PREPARING".equals(next);
            case "PREPARING" -> "READY".equals(next);
            case "READY" -> "SERVED".equals(next);
            default -> false;
        };
    }

    public List<OrderSummary> findAll() throws SQLException {
        String sql = """
                SELECT order_id, table_id, status, created_at
                FROM orders ORDER BY created_at DESC, order_id DESC
                """;
        List<OrderSummary> orders = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                orders.add(summary(result));
            }
        }
        return orders;
    }

    public OrderDetails findById(long orderId) throws SQLException {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        String orderSql = """
                SELECT order_id, table_id, status, created_at
                FROM orders WHERE order_id = ?
                """;
        String itemSql = """
                SELECT oi.menu_item_id, mi.item_name, oi.quantity, oi.unit_price
                FROM order_items oi JOIN menu_items mi
                  ON mi.menu_item_id = oi.menu_item_id
                WHERE oi.order_id = ? ORDER BY oi.order_item_id
                """;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement orderStatement = connection.prepareStatement(orderSql)) {
            orderStatement.setLong(1, orderId);
            try (ResultSet orderResult = orderStatement.executeQuery()) {
                if (!orderResult.next()) {
                    return null;
                }
                List<OrderItem> items = new ArrayList<>();
                try (PreparedStatement itemStatement = connection.prepareStatement(itemSql)) {
                    itemStatement.setLong(1, orderId);
                    try (ResultSet itemResult = itemStatement.executeQuery()) {
                        while (itemResult.next()) {
                            items.add(new OrderItem(
                                    itemResult.getLong("menu_item_id"),
                                    itemResult.getString("item_name"),
                                    itemResult.getInt("quantity"),
                                    itemResult.getBigDecimal("unit_price")));
                        }
                    }
                }
                return new OrderDetails(
                        orderResult.getLong("order_id"),
                        orderResult.getLong("table_id"),
                        orderResult.getString("status"),
                        String.valueOf(orderResult.getObject("created_at")),
                        items);
            }
        }
    }

    private OrderSummary summary(ResultSet result) throws SQLException {
        return new OrderSummary(
                result.getLong("order_id"),
                result.getLong("table_id"),
                result.getString("status"),
                String.valueOf(result.getObject("created_at")));
    }
}
