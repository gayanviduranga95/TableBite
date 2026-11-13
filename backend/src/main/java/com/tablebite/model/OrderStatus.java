package com.tablebite.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle:
 * PENDING -> CONFIRMED -> PREPARING -> READY -> SERVED -> COMPLETED
 * READY -> COMPLETED is allowed for takeaway pick-up.
 * PENDING / CONFIRMED -> CANCELLED (rejected or cancelled).
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY,
    SERVED,
    COMPLETED,
    CANCELLED;

    public static final Set<OrderStatus> ACTIVE =
            EnumSet.of(PENDING, CONFIRMED, PREPARING, READY);

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == PREPARING || next == CANCELLED;
            case PREPARING -> next == READY;
            case READY -> next == SERVED || next == COMPLETED;
            case SERVED -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Parses a status; the legacy value ACCEPTED is treated as CONFIRMED. */
    public static OrderStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        String normalized = value.trim().toUpperCase();
        if ("ACCEPTED".equals(normalized)) {
            return CONFIRMED;
        }
        try {
            return OrderStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported order status");
        }
    }
}
