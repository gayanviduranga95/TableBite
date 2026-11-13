package com.tablebite.database;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Payment records. Each order gets a PENDING payment when it is placed. */
public class PaymentRepository {

    public record Payment(long id, long orderId, String orderNumber, String tableNumber,
                          String customerName, String method, String status,
                          BigDecimal amount, String provider, String reference,
                          String receivedBy, String createdAt, String paidAt) {
    }

    /**
     * Confirms payment for an order (cash / counter / card terminal) collected by staff.
     * Runs in one transaction with the order row locked so it cannot be paid twice.
     */
    public void confirmPayment(long orderId, String method, String reference, Long staffUserId)
            throws SQLException {
        Jdbc.inTransaction(connection -> {
            BigDecimal total;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status, payment_status, total_amount FROM orders WHERE order_id = ? FOR UPDATE")) {
                statement.setLong(1, orderId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Order not found");
                    }
                    if ("CANCELLED".equals(result.getString("status"))) {
                        throw new IllegalArgumentException("Cancelled orders cannot be paid");
                    }
                    if ("PAID".equals(result.getString("payment_status"))) {
                        throw new IllegalArgumentException("This order is already paid");
                    }
                    total = result.getBigDecimal("total_amount");
                }
            }
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE payments SET status = 'PAID', method = ?, amount = ?, reference = ?,
                        received_by = ?, paid_at = NOW(),
                        provider = CASE WHEN ? = 'ONLINE' THEN 'ONLINE_GATEWAY' ELSE 'IN_PERSON' END
                    WHERE payment_id = (SELECT payment_id FROM payments
                                        WHERE order_id = ? AND status = 'PENDING'
                                        ORDER BY payment_id DESC LIMIT 1)""")) {
                statement.setString(1, method);
                statement.setBigDecimal(2, total);
                statement.setString(3, reference);
                Jdbc.setNullableLong(statement, 4, staffUserId);
                statement.setString(5, method);
                statement.setLong(6, orderId);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO payments (order_id, method, status, amount, reference,
                            received_by, paid_at, provider)
                        VALUES (?, ?, 'PAID', ?, ?, ?, NOW(),
                            CASE WHEN ? = 'ONLINE' THEN 'ONLINE_GATEWAY' ELSE 'IN_PERSON' END)""")) {
                    statement.setLong(1, orderId);
                    statement.setString(2, method);
                    statement.setBigDecimal(3, total);
                    statement.setString(4, reference);
                    Jdbc.setNullableLong(statement, 5, staffUserId);
                    statement.setString(6, method);
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE orders SET payment_status = 'PAID', payment_method = ?, updated_at = NOW()
                    WHERE order_id = ?""")) {
                statement.setString(1, method);
                statement.setLong(2, orderId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public List<Payment> findPayments(String status, String method, boolean todayOnly, int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT p.payment_id, p.order_id, o.order_number, t.table_number,
                       COALESCE(o.customer_name, cu.full_name) AS customer_name,
                       p.method, p.status, p.amount, p.provider, p.reference,
                       su.full_name AS received_by, p.created_at, p.paid_at
                FROM payments p
                JOIN orders o ON o.order_id = p.order_id
                LEFT JOIN restaurant_tables t ON t.table_id = o.table_id
                LEFT JOIN users cu ON cu.user_id = o.user_id
                LEFT JOIN users su ON su.user_id = p.received_by
                WHERE 1 = 1""");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ?");
            params.add(status.toUpperCase());
        }
        if (method != null && !method.isBlank()) {
            sql.append(" AND p.method = ?");
            params.add(method.toUpperCase());
        }
        if (todayOnly) {
            sql.append(" AND p.created_at >= date_trunc('day', NOW())");
        }
        sql.append(" ORDER BY COALESCE(p.paid_at, p.created_at) DESC, p.payment_id DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 1000)));
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    statement.setObject(i + 1, params.get(i));
                }
                List<Payment> payments = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        payments.add(new Payment(
                                result.getLong("payment_id"),
                                result.getLong("order_id"),
                                result.getString("order_number"),
                                result.getString("table_number"),
                                result.getString("customer_name"),
                                result.getString("method"),
                                result.getString("status"),
                                result.getBigDecimal("amount"),
                                result.getString("provider"),
                                result.getString("reference"),
                                result.getString("received_by"),
                                Jdbc.iso(result, "created_at"),
                                Jdbc.iso(result, "paid_at")));
                    }
                }
                return payments;
            }
        });
    }
}
