package com.tablebite.database;

import com.tablebite.model.Order;
import com.tablebite.model.OrderItem;
import com.tablebite.model.OrderStatus;
import com.tablebite.util.SecurityUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Order persistence. Order creation and status changes run inside a single
 * transaction with row locks so that prices, totals and status transitions are
 * always consistent.
 */
public class OrderRepository {

    public static final int MAX_LINES = 40;
    public static final int MAX_QUANTITY = 50;

    public record OrderItemInput(long menuItemId, int quantity, String notes) {
        public OrderItemInput(long menuItemId, int quantity) {
            this(menuItemId, quantity, null);
        }
    }

    public record NewOrder(Long tableId, String orderType, Long userId, String customerName,
                           String customerPhone, String specialInstructions,
                           String paymentMethod, String idempotencyKey,
                           BigDecimal taxRatePercent, BigDecimal serviceRatePercent,
                           List<OrderItemInput> items) {
    }

    public record CreateResult(long orderId, boolean duplicate) {
    }

    public record StatusChangeResult(OrderStatus previous, OrderStatus current) {
    }

    /** Optional filters for staff order lists. Null fields are ignored. */
    public record OrderFilter(String status, String query, String orderType, Long tableId,
                              Long userId, boolean todayOnly, boolean activeOnly,
                              String paymentStatus, int limit) {
        public static OrderFilter all() {
            return new OrderFilter(null, null, null, null, null, false, false, null, 200);
        }
    }

    /** Thrown inside a status-change transaction when a caller-specific rule rejects it. */
    public static class TransitionRejectedException extends IllegalArgumentException {
        public TransitionRejectedException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ create

    /**
     * Legacy entry point (TCP protocol and tests): a dine-in order paid at the counter,
     * priced with the configured tax / service-charge rates.
     */
    public long createOrder(long tableId, List<OrderItemInput> items) throws SQLException {
        if (tableId <= 0) {
            throw new IllegalArgumentException("Table ID must be positive");
        }
        Map<String, String> settings = new SettingsRepository().findAll();
        return create(new NewOrder(tableId, "DINE_IN", null, null, null, null,
                "PAY_AT_COUNTER", null,
                percent(settings.get("tax_rate")), percent(settings.get("service_charge_rate")),
                items)).orderId();
    }

    public CreateResult create(NewOrder order) throws SQLException {
        validate(order);
        if (order.idempotencyKey() != null) {
            Long existing = findIdByIdempotencyKey(order.idempotencyKey());
            if (existing != null) {
                return new CreateResult(existing, true);
            }
        }
        try {
            long id = Jdbc.inTransaction(connection -> insertOrder(connection, order));
            return new CreateResult(id, false);
        } catch (SQLException exception) {
            // A concurrent request with the same idempotency key won the race.
            if (Jdbc.isUniqueViolation(exception) && order.idempotencyKey() != null) {
                Long existing = findIdByIdempotencyKey(order.idempotencyKey());
                if (existing != null) {
                    return new CreateResult(existing, true);
                }
            }
            throw exception;
        }
    }

    private void validate(NewOrder order) {
        List<OrderItemInput> items = order.items();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }
        if (items.size() > MAX_LINES) {
            throw new IllegalArgumentException("An order can contain at most " + MAX_LINES + " lines");
        }
        for (OrderItemInput item : items) {
            if (item == null || item.menuItemId() <= 0) {
                throw new IllegalArgumentException("Invalid menu item");
            }
            if (item.quantity() <= 0 || item.quantity() > MAX_QUANTITY) {
                throw new IllegalArgumentException("Quantities must be between 1 and " + MAX_QUANTITY);
            }
        }
        if ("DINE_IN".equals(order.orderType()) && order.tableId() == null) {
            throw new IllegalArgumentException("Dine-in orders need a table");
        }
    }

    private long insertOrder(Connection connection, NewOrder order) throws SQLException {
        // 1. Lock and price every dish from the database (never trust client prices).
        Map<Long, PricedDish> dishes = loadDishes(connection, order.items());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemInput item : order.items()) {
            PricedDish dish = dishes.get(item.menuItemId());
            if (dish == null) {
                throw new IllegalArgumentException("A dish in your cart is no longer on the menu");
            }
            if (!dish.available()) {
                throw new IllegalArgumentException(dish.name() + " is currently unavailable");
            }
            subtotal = subtotal.add(dish.price().multiply(BigDecimal.valueOf(item.quantity())));
        }
        subtotal = money(subtotal);
        BigDecimal tax = money(subtotal.multiply(order.taxRatePercent()).movePointLeft(2));
        BigDecimal service = "DINE_IN".equals(order.orderType())
                ? money(subtotal.multiply(order.serviceRatePercent()).movePointLeft(2))
                : BigDecimal.ZERO.setScale(2);
        BigDecimal total = subtotal.add(tax).add(service);

        // 2. Order header.
        long orderId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO orders (table_id, status, order_type, user_id, customer_name,
                    customer_phone, special_instructions, subtotal, tax_amount, service_charge,
                    total_amount, payment_method, payment_status, tracking_token, idempotency_key)
                VALUES (?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNPAID', ?, ?)
                RETURNING order_id""")) {
            Jdbc.setNullableLong(statement, 1, order.tableId());
            statement.setString(2, order.orderType());
            Jdbc.setNullableLong(statement, 3, order.userId());
            statement.setString(4, order.customerName());
            statement.setString(5, order.customerPhone());
            statement.setString(6, order.specialInstructions());
            statement.setBigDecimal(7, subtotal);
            statement.setBigDecimal(8, tax);
            statement.setBigDecimal(9, service);
            statement.setBigDecimal(10, total);
            statement.setString(11, order.paymentMethod());
            statement.setString(12, SecurityUtil.randomToken(24));
            statement.setString(13, order.idempotencyKey());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                orderId = result.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE orders
                SET order_number = 'TB-' || to_char(created_at, 'YYMMDD') || '-' || lpad(order_id::text, 4, '0')
                WHERE order_id = ?""")) {
            statement.setLong(1, orderId);
            statement.executeUpdate();
        }

        // 3. Lines with a name/price snapshot.
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO order_items (order_id, menu_item_id, quantity, unit_price, item_name, notes)
                VALUES (?, ?, ?, ?, ?, ?)""")) {
            for (OrderItemInput item : order.items()) {
                PricedDish dish = dishes.get(item.menuItemId());
                statement.setLong(1, orderId);
                statement.setLong(2, item.menuItemId());
                statement.setInt(3, item.quantity());
                statement.setBigDecimal(4, dish.price());
                statement.setString(5, dish.name());
                statement.setString(6, item.notes());
                statement.addBatch();
            }
            statement.executeBatch();
        }

        // 4. Pending payment record + status history.
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payments (order_id, method, status, amount) VALUES (?, ?, 'PENDING', ?)""")) {
            statement.setLong(1, orderId);
            statement.setString(2, order.paymentMethod());
            statement.setBigDecimal(3, total);
            statement.executeUpdate();
        }
        insertHistory(connection, orderId, null, OrderStatus.PENDING, order.userId(), "Order placed");

        // 5. Mark the table as occupied.
        if (order.tableId() != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE restaurant_tables SET status = 'OCCUPIED' WHERE table_id = ? AND status = 'AVAILABLE'")) {
                statement.setLong(1, order.tableId());
                statement.executeUpdate();
            }
        }
        return orderId;
    }

    private record PricedDish(String name, BigDecimal price, boolean available) {
    }

    private Map<Long, PricedDish> loadDishes(Connection connection, List<OrderItemInput> items)
            throws SQLException {
        Long[] ids = items.stream().map(OrderItemInput::menuItemId).distinct().toArray(Long[]::new);
        Array array = connection.createArrayOf("bigint", ids);
        Map<Long, PricedDish> dishes = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT m.menu_item_id, m.item_name, m.price,
                       (m.available AND m.deleted_at IS NULL AND COALESCE(c.active, TRUE)) AS orderable
                FROM menu_items m LEFT JOIN categories c ON c.category_id = m.category_id
                WHERE m.menu_item_id = ANY(?)
                FOR SHARE OF m""")) {
            statement.setArray(1, array);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    dishes.put(result.getLong("menu_item_id"), new PricedDish(
                            result.getString("item_name"),
                            result.getBigDecimal("price"),
                            result.getBoolean("orderable")));
                }
            }
        }
        return dishes;
    }

    private Long findIdByIdempotencyKey(String key) throws SQLException {
        return Jdbc.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT order_id FROM orders WHERE idempotency_key = ?")) {
                statement.setString(1, key);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : null;
                }
            }
        });
    }

    // ------------------------------------------------------------------ status

    /** Legacy status update without an actor (TCP protocol). */
    public void updateStatus(long orderId, String status) throws SQLException {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Invalid order status update");
        }
        changeStatus(orderId, OrderStatus.parse(status), null, null, current -> { });
    }

    /**
     * Changes an order's status inside a transaction. {@code guard} receives the current
     * status (row-locked) and may throw {@link TransitionRejectedException} to veto.
     */
    public StatusChangeResult changeStatus(long orderId, OrderStatus target, Long actorUserId,
                                           String note, Consumer<OrderStatus> guard)
            throws SQLException {
        return Jdbc.inTransaction(connection -> {
            OrderStatus current;
            Long tableId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status, table_id FROM orders WHERE order_id = ? FOR UPDATE")) {
                statement.setLong(1, orderId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Order not found");
                    }
                    current = OrderStatus.parse(result.getString("status"));
                    tableId = Jdbc.nullableLong(result, "table_id");
                }
            }
            guard.accept(current);
            if (!current.canTransitionTo(target)) {
                throw new IllegalArgumentException(
                        "Cannot change order from " + current + " to " + target);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE orders SET status = ?, updated_at = NOW(),
                        cancel_reason = CASE WHEN ? = 'CANCELLED' THEN ? ELSE cancel_reason END
                    WHERE order_id = ?""")) {
                statement.setString(1, target.name());
                statement.setString(2, target.name());
                statement.setString(3, note);
                statement.setLong(4, orderId);
                statement.executeUpdate();
            }
            if (target == OrderStatus.CANCELLED) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE payments SET status = 'CANCELLED' WHERE order_id = ? AND status = 'PENDING'")) {
                    statement.setLong(1, orderId);
                    statement.executeUpdate();
                }
            }
            insertHistory(connection, orderId, current, target, actorUserId, note);
            if (target.isTerminal() && tableId != null) {
                releaseTableIfIdle(connection, tableId);
            }
            return new StatusChangeResult(current, target);
        });
    }

    private void releaseTableIfIdle(Connection connection, long tableId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE restaurant_tables SET status = 'AVAILABLE'
                WHERE table_id = ? AND status = 'OCCUPIED'
                  AND NOT EXISTS (SELECT 1 FROM orders WHERE table_id = ?
                      AND status IN ('PENDING','CONFIRMED','PREPARING','READY','SERVED'))""")) {
            statement.setLong(1, tableId);
            statement.setLong(2, tableId);
            statement.executeUpdate();
        }
    }

    private void insertHistory(Connection connection, long orderId, OrderStatus from,
                               OrderStatus to, Long actor, String note) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, note)
                VALUES (?, ?, ?, ?, ?)""")) {
            statement.setLong(1, orderId);
            statement.setString(2, from == null ? null : from.name());
            statement.setString(3, to.name());
            Jdbc.setNullableLong(statement, 4, actor);
            statement.setString(5, note);
            statement.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ queries

    private static final String SELECT = """
            SELECT o.order_id, o.order_number, o.table_id, t.table_number, o.order_type, o.status,
                   o.user_id, COALESCE(o.customer_name, u.full_name) AS customer_name,
                   COALESCE(o.customer_phone, u.phone) AS customer_phone,
                   o.special_instructions, o.subtotal, o.tax_amount, o.service_charge,
                   o.total_amount, o.payment_method, o.payment_status, o.cancel_reason,
                   o.tracking_token, o.created_at, o.updated_at,
                   (SELECT COALESCE(SUM(quantity), 0) FROM order_items WHERE order_id = o.order_id) AS item_count
            FROM orders o
            LEFT JOIN restaurant_tables t ON t.table_id = o.table_id
            LEFT JOIN users u ON u.user_id = o.user_id
            """;

    /** Legacy: every order, newest first, without lines. */
    public List<Order> findAll() throws SQLException {
        return findOrders(OrderFilter.all(), false);
    }

    public Order findById(long orderId) throws SQLException {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        return Jdbc.withConnection(connection -> {
            Order order;
            try (PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE o.order_id = ?")) {
                statement.setLong(1, orderId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    order = map(result);
                }
            }
            Map<Long, List<OrderItem>> items = loadItems(connection, List.of(orderId));
            return order.withDetails(items.getOrDefault(orderId, List.of()),
                    loadHistory(connection, orderId));
        });
    }

    public List<Order> findOrders(OrderFilter filter, boolean withItems) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (filter.status() != null && !filter.status().isBlank()) {
            sql.append(" AND o.status = ?");
            params.add(OrderStatus.parse(filter.status()).name());
        }
        if (filter.activeOnly()) {
            sql.append(" AND o.status IN ('PENDING','CONFIRMED','PREPARING','READY')");
        }
        if (filter.orderType() != null && !filter.orderType().isBlank()) {
            sql.append(" AND o.order_type = ?");
            params.add(filter.orderType().toUpperCase());
        }
        if (filter.paymentStatus() != null && !filter.paymentStatus().isBlank()) {
            sql.append(" AND o.payment_status = ?");
            params.add(filter.paymentStatus().toUpperCase());
        }
        if (filter.tableId() != null) {
            sql.append(" AND o.table_id = ?");
            params.add(filter.tableId());
        }
        if (filter.userId() != null) {
            sql.append(" AND o.user_id = ?");
            params.add(filter.userId());
        }
        if (filter.todayOnly()) {
            sql.append(" AND o.created_at >= date_trunc('day', NOW())");
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            String like = "%" + filter.query().trim().replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" AND (o.order_number ILIKE ? OR t.table_number ILIKE ?"
                    + " OR COALESCE(o.customer_name, u.full_name) ILIKE ?"
                    + " OR COALESCE(o.customer_phone, u.phone) ILIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(filter.activeOnly()
                ? " ORDER BY o.created_at ASC, o.order_id ASC"
                : " ORDER BY o.created_at DESC, o.order_id DESC");
        sql.append(" LIMIT ?");
        params.add(Math.max(1, Math.min(filter.limit() <= 0 ? 200 : filter.limit(), 1000)));

        return Jdbc.withConnection(connection -> {
            List<Order> orders = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    statement.setObject(i + 1, params.get(i));
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        orders.add(map(result));
                    }
                }
            }
            if (!withItems || orders.isEmpty()) {
                return orders;
            }
            Map<Long, List<OrderItem>> items = loadItems(connection,
                    orders.stream().map(Order::id).toList());
            List<Order> detailed = new ArrayList<>(orders.size());
            for (Order order : orders) {
                detailed.add(order.withDetails(items.getOrDefault(order.id(), List.of()), null));
            }
            return detailed;
        });
    }

    private Map<Long, List<OrderItem>> loadItems(Connection connection, List<Long> orderIds)
            throws SQLException {
        Map<Long, List<OrderItem>> items = new LinkedHashMap<>();
        Array array = connection.createArrayOf("bigint", orderIds.toArray(Long[]::new));
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT oi.order_id, oi.menu_item_id, COALESCE(oi.item_name, mi.item_name) AS item_name,
                       oi.quantity, oi.unit_price, oi.notes, mi.image_url
                FROM order_items oi JOIN menu_items mi ON mi.menu_item_id = oi.menu_item_id
                WHERE oi.order_id = ANY(?)
                ORDER BY oi.order_id, oi.order_item_id""")) {
            statement.setArray(1, array);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    BigDecimal unit = result.getBigDecimal("unit_price");
                    int quantity = result.getInt("quantity");
                    items.computeIfAbsent(result.getLong("order_id"), ignored -> new ArrayList<>())
                            .add(new OrderItem(
                                    result.getLong("menu_item_id"),
                                    result.getString("item_name"),
                                    quantity,
                                    unit,
                                    money(unit.multiply(BigDecimal.valueOf(quantity))),
                                    result.getString("notes"),
                                    result.getString("image_url")));
                }
            }
        }
        return items;
    }

    private List<Order.StatusChange> loadHistory(Connection connection, long orderId)
            throws SQLException {
        List<Order.StatusChange> history = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT h.from_status, h.to_status, h.note, u.full_name, h.changed_at
                FROM order_status_history h LEFT JOIN users u ON u.user_id = h.changed_by
                WHERE h.order_id = ? ORDER BY h.changed_at, h.history_id""")) {
            statement.setLong(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    history.add(new Order.StatusChange(
                            result.getString("from_status"),
                            result.getString("to_status"),
                            result.getString("note"),
                            result.getString("full_name"),
                            Jdbc.iso(result, "changed_at")));
                }
            }
        }
        return history;
    }

    private Order map(ResultSet result) throws SQLException {
        return new Order(
                result.getLong("order_id"),
                result.getString("order_number"),
                Jdbc.nullableLong(result, "table_id"),
                result.getString("table_number"),
                result.getString("order_type"),
                result.getString("status"),
                Jdbc.nullableLong(result, "user_id"),
                result.getString("customer_name"),
                result.getString("customer_phone"),
                result.getString("special_instructions"),
                result.getBigDecimal("subtotal"),
                result.getBigDecimal("tax_amount"),
                result.getBigDecimal("service_charge"),
                result.getBigDecimal("total_amount"),
                result.getString("payment_method"),
                result.getString("payment_status"),
                result.getString("cancel_reason"),
                result.getString("tracking_token"),
                Jdbc.iso(result, "created_at"),
                Jdbc.iso(result, "updated_at"),
                result.getInt("item_count"),
                null,
                null);
    }

    // ------------------------------------------------------------------ helpers

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            return parsed.signum() < 0 ? BigDecimal.ZERO : parsed;
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }
}
