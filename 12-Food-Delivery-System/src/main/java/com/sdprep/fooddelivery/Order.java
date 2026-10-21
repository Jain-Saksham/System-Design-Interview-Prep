package com.sdprep.fooddelivery;

import java.util.ArrayList;
import java.util.List;

/** One order and its full status history. All changes go through {@link #moveTo}. */
public class Order {

    public record StatusChange(String orderId, OrderStatus status, long atMillis) {
    }

    private final String id;
    private final String customerId;
    private final Location deliveryLocation;
    private final Restaurant restaurant;
    private final long totalCents;
    private final List<StatusChange> history = new ArrayList<>();
    private OrderStatus status;
    private String courierId;

    Order(String id, String customerId, Location deliveryLocation, Restaurant restaurant, long totalCents, long nowMillis) {
        this.id = id;
        this.customerId = customerId;
        this.deliveryLocation = deliveryLocation;
        this.restaurant = restaurant;
        this.totalCents = totalCents;
        this.status = OrderStatus.PLACED;
        history.add(new StatusChange(id, OrderStatus.PLACED, nowMillis));
    }

    public String id() { return id; }
    public String customerId() { return customerId; }
    public Location deliveryLocation() { return deliveryLocation; }
    public Restaurant restaurant() { return restaurant; }
    public long totalCents() { return totalCents; }
    public synchronized OrderStatus status() { return status; }
    public synchronized String courierId() { return courierId; }
    public synchronized List<StatusChange> history() { return List.copyOf(history); }

    synchronized StatusChange moveTo(OrderStatus next, long nowMillis) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Order " + id + " cannot go from " + status + " to " + next);
        }
        status = next;
        StatusChange change = new StatusChange(id, next, nowMillis);
        history.add(change);
        return change;
    }

    synchronized void assignCourier(String courierId) {
        this.courierId = courierId;
    }
}
