package com.tablebite.service;

import com.tablebite.database.OrderRepository;
import com.tablebite.model.Order;
import com.tablebite.server.LiveWebSocketServer;
import com.tablebite.util.LoggerUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pushes real-time order notifications over the WebSocket server:
 * staff screens (kitchen / floor / admin) receive every event, customers receive
 * events for the order they are tracking and, when signed in, for all of their orders.
 */
public final class NotificationService {
    private static final Logger LOG = LoggerUtil.get(NotificationService.class);

    private NotificationService() {
    }

    public static void orderCreated(Order order) {
        publish("ORDER_CREATED", order, "New order " + order.orderNumber() + " placed"
                + (order.tableNumber() == null ? " (takeaway)" : " at table " + order.tableNumber()));
    }

    public static void orderStatusChanged(Order order) {
        publish("ORDER_STATUS_CHANGED", order, customerMessage(order));
    }

    public static void paymentUpdated(Order order) {
        publish("PAYMENT_UPDATED", order, "Payment received for order " + order.orderNumber());
    }

    /** Used by the raw TCP server, which only knows the order id. */
    public static void orderChanged(long orderId, boolean created) {
        if (LiveWebSocketServer.getInstance() == null) {
            return;
        }
        try {
            Order order = new OrderRepository().findById(orderId);
            if (order != null) {
                if (created) {
                    orderCreated(order);
                } else {
                    orderStatusChanged(order);
                }
            }
        } catch (Exception exception) {
            LOG.log(Level.WARNING, "Could not load order " + orderId + " for notification", exception);
        }
    }

    private static void publish(String type, Order order, String message) {
        LiveWebSocketServer server = LiveWebSocketServer.getInstance();
        if (server == null || order == null) {
            return;
        }
        try {
            server.publishOrderEvent(type, order.withoutToken(), message);
        } catch (RuntimeException exception) {
            LOG.log(Level.WARNING, "WebSocket notification failed", exception);
        }
    }

    static String customerMessage(Order order) {
        String number = order.orderNumber();
        return switch (order.status()) {
            case "CONFIRMED" -> "Order " + number + " was accepted by the kitchen";
            case "PREPARING" -> "Order " + number + " is being prepared";
            case "READY" -> "TAKEAWAY".equals(order.orderType())
                    ? "Order " + number + " is ready for pick-up"
                    : "Order " + number + " is ready and on its way to your table";
            case "SERVED" -> "Order " + number + " has been served. Enjoy your meal!";
            case "COMPLETED" -> "Order " + number + " is complete. Thank you!";
            case "CANCELLED" -> "Order " + number + " was cancelled"
                    + (order.cancelReason() == null ? "" : ": " + order.cancelReason());
            default -> "Order " + number + " was updated";
        };
    }
}
