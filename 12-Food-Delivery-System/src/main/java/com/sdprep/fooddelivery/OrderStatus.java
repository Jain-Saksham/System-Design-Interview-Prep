package com.sdprep.fooddelivery;

import java.util.EnumSet;
import java.util.Set;

/**
 * The order lifecycle as a state machine. Each status lists the statuses it may move to;
 * anything else is rejected, so an order can't be "delivered" before it was picked up.
 */
public enum OrderStatus {
    PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, PICKED_UP, DELIVERED, CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> EnumSet.of(ACCEPTED, CANCELLED);
            case ACCEPTED -> EnumSet.of(PREPARING, CANCELLED);   // last chance to cancel: food not cooked yet
            case PREPARING -> EnumSet.of(READY_FOR_PICKUP);
            case READY_FOR_PICKUP -> EnumSet.of(PICKED_UP);
            case PICKED_UP -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class); // final states
        };
    }

    public boolean canMoveTo(OrderStatus next) {
        return allowedNext().contains(next);
    }
}
