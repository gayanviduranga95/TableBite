
package com.tablebite.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class OrderRepository {

    public record OrderItemInput(long menuItemId, int quantity) {}

    public long createOrder(
            long tableId,
            List<OrderItemInput> items
    ) throws SQLException {

        if (tableId <= 0) {
            throw new IllegalArgumentException("Table ID must be positive");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                "An order must contain at least one item"
            );
        }

        for (OrderItemInput item : items) {
            if (item == null) {
                throw new IllegalArgumentException("Order items cannot be null");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
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

                try (PreparedStatement statement =
                        connection.prepareStatement(orderSql)) {

                    statement.setLong(1, tableId);

                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        orderId = result.getLong("order_id");
                    }
                }

                String priceSql = """
                    SELECT price
                    FROM menu_items
                    WHERE menu_item_id = ?
                      AND available = TRUE
                    """;

                String itemSql = """
                    INSERT INTO order_items
                        (order_id, menu_item_id, quantity, unit_price)
                    VALUES (?, ?, ?, ?)
                    """;

                try (PreparedStatement priceStatement =
                        connection.prepareStatement(priceSql);
                     PreparedStatement itemStatement =
                        connection.prepareStatement(itemSql)) {
                    for (OrderItemInput item : items) {
                        BigDecimal price;

                        priceStatement.setLong(1, item.menuItemId());

                        try (ResultSet result =
                                priceStatement.executeQuery()) {

                            if (!result.next()) {
                                throw new SQLException(
                                    "Menu item not found or unavailable: "
                                    + item.menuItemId()
                                );
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

                throw new SQLException(
                    "Could not create order",
                    exception
                );
            }
        }
    }
}
