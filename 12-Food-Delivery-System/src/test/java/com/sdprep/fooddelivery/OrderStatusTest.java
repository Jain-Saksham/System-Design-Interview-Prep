package com.sdprep.fooddelivery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertTrue(OrderStatus.PLACED.canMoveTo(OrderStatus.ACCEPTED));
        assertTrue(OrderStatus.ACCEPTED.canMoveTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING.canMoveTo(OrderStatus.READY_FOR_PICKUP));
        assertTrue(OrderStatus.READY_FOR_PICKUP.canMoveTo(OrderStatus.PICKED_UP));
        assertTrue(OrderStatus.PICKED_UP.canMoveTo(OrderStatus.DELIVERED));
    }

    @Test
    void skippingStepsIsNotAllowed() {
        assertFalse(OrderStatus.PLACED.canMoveTo(OrderStatus.DELIVERED));
        assertFalse(OrderStatus.ACCEPTED.canMoveTo(OrderStatus.PICKED_UP));
    }

    @Test
    void cancellationOnlyBeforeCookingStarts() {
        assertTrue(OrderStatus.PLACED.canMoveTo(OrderStatus.CANCELLED));
        assertTrue(OrderStatus.ACCEPTED.canMoveTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.PREPARING.canMoveTo(OrderStatus.CANCELLED));
    }

    @Test
    void finalStatesAreFinal() {
        assertTrue(OrderStatus.DELIVERED.allowedNext().isEmpty());
        assertTrue(OrderStatus.CANCELLED.allowedNext().isEmpty());
    }
}
