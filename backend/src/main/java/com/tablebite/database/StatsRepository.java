package com.tablebite.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates for the admin dashboard. Revenue excludes cancelled orders. */
public class StatsRepository {

    public record Totals(int totalOrders, int todayOrders, BigDecimal totalRevenue,
                         BigDecimal todayRevenue, BigDecimal paidRevenue,
                         BigDecimal averageOrderValue, int pending, int confirmed,
                         int preparing, int ready, int served, int completed, int cancelled,
                         int dineIn, int takeaway, int customers, int activeTables, int tables) {
    }

    public record DayRevenue(String day, int orders, BigDecimal revenue) {
    }

    public record PopularItem(long id, String name, String imageUrl, int quantity,
                              BigDecimal revenue) {
    }

    public record Dashboard(Totals totals, List<DayRevenue> revenueByDay,
                            List<PopularItem> popularItems, Map<String, Integer> ordersByHour) {
    }

    public Dashboard dashboard(int days) throws SQLException {
        int span = Math.max(7, Math.min(days, 90));
        return Jdbc.withConnection(connection -> new Dashboard(
                totals(connection), revenueByDay(connection, span),
                popularItems(connection), ordersByHour(connection)));
    }

    private Totals totals(Connection connection) throws SQLException {
        String sql = """
                SELECT
                  COUNT(*) AS total_orders,
                  COUNT(*) FILTER (WHERE created_at >= date_trunc('day', NOW())) AS today_orders,
                  COALESCE(SUM(total_amount) FILTER (WHERE status <> 'CANCELLED'), 0) AS total_revenue,
                  COALESCE(SUM(total_amount) FILTER (WHERE status <> 'CANCELLED'
                       AND created_at >= date_trunc('day', NOW())), 0) AS today_revenue,
                  COALESCE(SUM(total_amount) FILTER (WHERE payment_status = 'PAID'), 0) AS paid_revenue,
                  COALESCE(AVG(total_amount) FILTER (WHERE status <> 'CANCELLED'), 0) AS avg_value,
                  COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
                  COUNT(*) FILTER (WHERE status = 'CONFIRMED') AS confirmed,
                  COUNT(*) FILTER (WHERE status = 'PREPARING') AS preparing,
                  COUNT(*) FILTER (WHERE status = 'READY') AS ready,
                  COUNT(*) FILTER (WHERE status = 'SERVED') AS served,
                  COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                  COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled,
                  COUNT(*) FILTER (WHERE order_type = 'DINE_IN') AS dine_in,
                  COUNT(*) FILTER (WHERE order_type = 'TAKEAWAY') AS takeaway,
                  (SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER') AS customers,
                  (SELECT COUNT(*) FROM restaurant_tables WHERE deleted_at IS NULL AND status = 'OCCUPIED') AS active_tables,
                  (SELECT COUNT(*) FROM restaurant_tables WHERE deleted_at IS NULL) AS tables
                FROM orders""";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return new Totals(
                    result.getInt("total_orders"), result.getInt("today_orders"),
                    OrderRepository.money(result.getBigDecimal("total_revenue")),
                    OrderRepository.money(result.getBigDecimal("today_revenue")),
                    OrderRepository.money(result.getBigDecimal("paid_revenue")),
                    OrderRepository.money(result.getBigDecimal("avg_value")),
                    result.getInt("pending"), result.getInt("confirmed"),
                    result.getInt("preparing"), result.getInt("ready"),
                    result.getInt("served"), result.getInt("completed"),
                    result.getInt("cancelled"), result.getInt("dine_in"),
                    result.getInt("takeaway"), result.getInt("customers"),
                    result.getInt("active_tables"), result.getInt("tables"));
        }
    }

    private List<DayRevenue> revenueByDay(Connection connection, int days) throws SQLException {
        String sql = """
                SELECT to_char(d.day, 'YYYY-MM-DD') AS day,
                       COUNT(o.order_id) AS orders,
                       COALESCE(SUM(o.total_amount), 0) AS revenue
                FROM generate_series(date_trunc('day', NOW()) - make_interval(days => ?),
                                     date_trunc('day', NOW()), INTERVAL '1 day') AS d(day)
                LEFT JOIN orders o ON o.created_at >= d.day AND o.created_at < d.day + INTERVAL '1 day'
                     AND o.status <> 'CANCELLED'
                GROUP BY d.day ORDER BY d.day""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, days - 1);
            List<DayRevenue> rows = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new DayRevenue(result.getString("day"), result.getInt("orders"),
                            OrderRepository.money(result.getBigDecimal("revenue"))));
                }
            }
            return rows;
        }
    }

    private List<PopularItem> popularItems(Connection connection) throws SQLException {
        String sql = """
                SELECT mi.menu_item_id, COALESCE(MAX(oi.item_name), mi.item_name) AS name, mi.image_url,
                       SUM(oi.quantity) AS qty, SUM(oi.quantity * oi.unit_price) AS revenue
                FROM order_items oi
                JOIN orders o ON o.order_id = oi.order_id AND o.status <> 'CANCELLED'
                JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                GROUP BY mi.menu_item_id, mi.item_name, mi.image_url
                ORDER BY qty DESC, revenue DESC LIMIT 6""";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<PopularItem> items = new ArrayList<>();
            while (result.next()) {
                items.add(new PopularItem(result.getLong("menu_item_id"), result.getString("name"),
                        result.getString("image_url"), result.getInt("qty"),
                        OrderRepository.money(result.getBigDecimal("revenue"))));
            }
            return items;
        }
    }

    private Map<String, Integer> ordersByHour(Connection connection) throws SQLException {
        String sql = """
                SELECT EXTRACT(HOUR FROM created_at)::int AS hour, COUNT(*) AS orders
                FROM orders WHERE created_at >= date_trunc('day', NOW())
                GROUP BY 1 ORDER BY 1""";
        Map<String, Integer> byHour = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                byHour.put(String.valueOf(result.getInt("hour")), result.getInt("orders"));
            }
        }
        return byHour;
    }
}
