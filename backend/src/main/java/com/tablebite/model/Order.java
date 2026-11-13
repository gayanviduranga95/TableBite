package com.tablebite.model;

import java.math.BigDecimal;
import java.util.List;

/** Full order view used by the HTTP API, WebSocket events and dashboards. */
public record Order(long id, String orderNumber, Long tableId, String tableNumber,
                    String orderType, String status, Long userId, String customerName,
                    String customerPhone, String specialInstructions,
                    BigDecimal subtotal, BigDecimal taxAmount, BigDecimal serviceCharge,
                    BigDecimal totalAmount, String paymentMethod, String paymentStatus,
                    String cancelReason, String trackingToken, String createdAt,
                    String updatedAt, int itemCount, List<OrderItem> items,
                    List<StatusChange> history) {

    public record StatusChange(String fromStatus, String toStatus, String note,
                               String changedBy, String changedAt) {
    }

    public Order withDetails(List<OrderItem> newItems, List<StatusChange> newHistory) {
        return new Order(id, orderNumber, tableId, tableNumber, orderType, status, userId,
                customerName, customerPhone, specialInstructions, subtotal, taxAmount,
                serviceCharge, totalAmount, paymentMethod, paymentStatus, cancelReason,
                trackingToken, createdAt, updatedAt, itemCount, newItems, newHistory);
    }

    /** Copy without the secret tracking token (for staff lists and broadcasts). */
    public Order withoutToken() {
        return new Order(id, orderNumber, tableId, tableNumber, orderType, status, userId,
                customerName, customerPhone, specialInstructions, subtotal, taxAmount,
                serviceCharge, totalAmount, paymentMethod, paymentStatus, cancelReason,
                null, createdAt, updatedAt, itemCount, items, history);
    }
}
