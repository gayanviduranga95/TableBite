package com.tablebite.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void followsTheHappyPath() {
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED));
        assertTrue(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY));
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.SERVED));
        assertTrue(OrderStatus.SERVED.canTransitionTo(OrderStatus.COMPLETED));
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.COMPLETED), "takeaway pick-up");
    }

    @Test
    void rejectsSkippingStepsAndLateCancellation() {
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.PREPARING));
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.READY));
        assertFalse(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.READY.canTransitionTo(OrderStatus.PENDING));
    }

    @Test
    void terminalStatesAreFinal() {
        for (OrderStatus next : OrderStatus.values()) {
            assertFalse(OrderStatus.COMPLETED.canTransitionTo(next));
            assertFalse(OrderStatus.CANCELLED.canTransitionTo(next));
        }
    }

    @Test
    void parsesLegacyAcceptedAsConfirmed() {
        assertEquals(OrderStatus.CONFIRMED, OrderStatus.parse("accepted"));
        assertEquals(OrderStatus.READY, OrderStatus.parse(" ready "));
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.parse("COOKED"));
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.parse(""));
    }
}
