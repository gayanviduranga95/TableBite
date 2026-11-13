package com.tablebite.service;

import com.tablebite.database.OrderRepository;
import com.tablebite.database.PaymentRepository;
import com.tablebite.database.SettingsRepository;
import com.tablebite.database.TableRepository;
import com.tablebite.model.Order;
import com.tablebite.model.OrderStatus;
import com.tablebite.model.RestaurantTable;
import com.tablebite.model.User;
import com.tablebite.model.UserRole;
import com.tablebite.payment.PaymentGateways;
import com.tablebite.util.SecurityUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Order business rules: pricing inputs, who may change which status, customer
 * cancellation, payments and notifications.
 */
public class OrderService {

    public record PlaceOrderRequest(String orderType, String tableToken,
                                    List<OrderRepository.OrderItemInput> items,
                                    String specialInstructions, String customerName,
                                    String customerPhone, String paymentMethod,
                                    String idempotencyKey) {
    }

    public record PlaceOrderResult(Order order, boolean duplicate) {
    }

    private static final Set<String> CUSTOMER_PAYMENT_METHODS = Set.of("CASH", "PAY_AT_COUNTER", "ONLINE");
    private static final Set<String> STAFF_PAYMENT_METHODS = Set.of("CASH", "CARD", "PAY_AT_COUNTER");

    private final OrderRepository orders;
    private final TableRepository tables;
    private final SettingsRepository settings;
    private final PaymentRepository payments;

    public OrderService() {
        this(new OrderRepository(), new TableRepository(), new SettingsRepository(),
                new PaymentRepository());
    }

    public OrderService(OrderRepository orders, TableRepository tables,
                        SettingsRepository settings, PaymentRepository payments) {
        this.orders = orders;
        this.tables = tables;
        this.settings = settings;
        this.payments = payments;
    }

    // ------------------------------------------------------------------ customer

    public PlaceOrderResult placeOrder(PlaceOrderRequest request, User customer) throws SQLException {
        Map<String, String> config = settings.findAll();
        String type = request.orderType() == null || request.orderType().isBlank()
                ? "DINE_IN" : request.orderType().trim().toUpperCase();
        if (!type.equals("DINE_IN") && !type.equals("TAKEAWAY")) {
            throw new IllegalArgumentException("Order type must be DINE_IN or TAKEAWAY");
        }

        Long tableId = null;
        if (type.equals("DINE_IN")) {
            RestaurantTable table = tables.findByQrToken(request.tableToken());
            if (table == null) {
                throw new ServiceException(404, "TABLE_NOT_FOUND",
                        "We couldn't find your table. Please scan the QR code on your table again.");
            }
            if ("OUT_OF_SERVICE".equals(table.status())) {
                throw new IllegalArgumentException("This table is not taking orders right now. Please ask our staff.");
            }
            tableId = table.id();
        } else if (!"true".equalsIgnoreCase(config.getOrDefault("allow_takeaway", "true"))) {
            throw new IllegalArgumentException("Takeaway orders are not available right now");
        }

        String name = Validation.optionalText(request.customerName(), "Name", 120);
        String phone = Validation.optionalPhone(request.customerPhone());
        if (type.equals("TAKEAWAY")) {
            if (name == null && customer == null) {
                throw new IllegalArgumentException("Please enter a name for your takeaway order");
            }
            if (phone == null && (customer == null || customer.phone() == null)) {
                throw new IllegalArgumentException("Please enter a phone number for your takeaway order");
            }
        }

        String method = request.paymentMethod() == null || request.paymentMethod().isBlank()
                ? "PAY_AT_COUNTER" : request.paymentMethod().trim().toUpperCase();
        if (!CUSTOMER_PAYMENT_METHODS.contains(method)) {
            throw new IllegalArgumentException("Unsupported payment method");
        }
        if (method.equals("ONLINE") && !PaymentGateways.onlineEnabled()) {
            throw new IllegalArgumentException(
                    "Online payment is not available. Please choose cash or pay at the counter.");
        }

        for (OrderRepository.OrderItemInput item : request.items()) {
            Validation.optionalText(item.notes(), "Item note", 255);
        }

        OrderRepository.CreateResult created = orders.create(new OrderRepository.NewOrder(
                tableId, type, customer == null || customer.role() != UserRole.CUSTOMER ? null : customer.id(),
                name, phone,
                Validation.optionalText(request.specialInstructions(), "Special instructions", 500),
                method, Validation.idempotencyKey(request.idempotencyKey()),
                OrderRepository.percent(config.get("tax_rate")),
                OrderRepository.percent(config.get("service_charge_rate")),
                request.items()));
        Order order = orders.findById(created.orderId());
        if (!created.duplicate()) {
            NotificationService.orderCreated(order);
        }
        return new PlaceOrderResult(order, created.duplicate());
    }

    /**
     * Returns an order if the viewer may see it: staff, the signed-in owner, or anyone
     * holding the order's secret tracking token.
     */
    public Order getForViewer(long orderId, String trackingToken, User viewer) throws SQLException {
        Order order = orders.findById(orderId);
        if (order == null || !canView(order, trackingToken, viewer)) {
            // Same answer for "missing" and "not yours" so ids cannot be probed.
            throw ServiceException.notFound("Order");
        }
        return order;
    }

    public List<Order> ordersForCustomer(User customer) throws SQLException {
        return orders.findOrders(new OrderRepository.OrderFilter(null, null, null, null,
                customer.id(), false, false, null, 100), true);
    }

    public Order cancelByCustomer(long orderId, String trackingToken, User viewer, String reason)
            throws SQLException {
        Order order = getForViewer(orderId, trackingToken, viewer);
        String note = Validation.optionalText(reason, "Reason", 255);
        orders.changeStatus(order.id(), OrderStatus.CANCELLED,
                viewer == null || viewer.id() == 0 ? null : viewer.id(),
                note == null ? "Cancelled by customer" : note,
                current -> {
                    if (current != OrderStatus.PENDING) {
                        throw new OrderRepository.TransitionRejectedException(
                                "This order has already been accepted by the kitchen and can no longer be cancelled. Please speak to our staff.");
                    }
                });
        Order updated = orders.findById(orderId);
        NotificationService.orderStatusChanged(updated);
        return updated;
    }

    private boolean canView(Order order, String trackingToken, User viewer) {
        if (viewer != null && viewer.role().isStaff()) {
            return true;
        }
        if (viewer != null && order.userId() != null && order.userId() == viewer.id()) {
            return true;
        }
        return trackingToken != null && SecurityUtil.constantTimeEquals(trackingToken, order.trackingToken());
    }

    // ------------------------------------------------------------------ staff

    public List<Order> listForStaff(OrderRepository.OrderFilter filter, boolean withItems)
            throws SQLException {
        return orders.findOrders(filter, withItems).stream().map(Order::withoutToken).toList();
    }

    public Order getForStaff(long orderId) throws SQLException {
        Order order = orders.findById(orderId);
        if (order == null) {
            throw ServiceException.notFound("Order");
        }
        return order.withoutToken();
    }

    public Order updateStatus(long orderId, String statusValue, User staff, String reason)
            throws SQLException {
        OrderStatus target = OrderStatus.parse(statusValue);
        requireStatusPermission(staff.role(), target);
        String note = Validation.optionalText(reason, "Reason", 255);
        orders.changeStatus(orderId, target, staff.id() == 0 ? null : staff.id(),
                note, current -> { });
        Order updated = orders.findById(orderId);
        NotificationService.orderStatusChanged(updated);
        return updated.withoutToken();
    }

    private void requireStatusPermission(UserRole role, OrderStatus target) {
        boolean allowed = switch (target) {
            case CONFIRMED, PREPARING, READY -> role.canUseKitchen();
            case SERVED, COMPLETED -> role.canUseFloor();
            case CANCELLED -> role.isStaff();
            case PENDING -> false;
        };
        if (!allowed) {
            throw ServiceException.forbidden("Your role cannot set orders to " + target);
        }
    }

    public Order confirmPayment(long orderId, String method, String reference, User staff)
            throws SQLException {
        if (!staff.role().canUseFloor()) {
            throw ServiceException.forbidden("Only floor staff or admins can record payments");
        }
        String normalized = method == null || method.isBlank() ? "CASH" : method.trim().toUpperCase();
        if (!STAFF_PAYMENT_METHODS.contains(normalized)) {
            throw new IllegalArgumentException("Payment method must be CASH, CARD or PAY_AT_COUNTER");
        }
        payments.confirmPayment(orderId, normalized,
                Validation.optionalText(reference, "Reference", 100),
                staff.id() == 0 ? null : staff.id());
        Order updated = orders.findById(orderId);
        NotificationService.paymentUpdated(updated);
        return updated.withoutToken();
    }
}
