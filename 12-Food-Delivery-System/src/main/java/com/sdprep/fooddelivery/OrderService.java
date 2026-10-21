package com.sdprep.fooddelivery;

import com.sdprep.fooddelivery.Order.StatusChange;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Coordinates customers, restaurants, and couriers around one order's lifecycle.
 * Every status change is published to subscribers (the customer's tracking screen).
 *
 * <p>Production: each step is an API call from a different app (customer, restaurant tablet,
 * courier), and status events flow through Kafka to push notifications and WebSockets.
 */
public class OrderService {

    private final Map<String, Restaurant> restaurants;
    private final DispatchService dispatch;
    private final LongSupplier clockMillis;
    private final Map<String, Order> ordersById = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<StatusChange>>> subscribersByOrder = new ConcurrentHashMap<>();
    private final AtomicLong nextOrderId = new AtomicLong(1001);

    public OrderService(List<Restaurant> restaurants, DispatchService dispatch, LongSupplier clockMillis) {
        this.restaurants = restaurants.stream().collect(Collectors.toMap(Restaurant::id, Function.identity()));
        this.dispatch = dispatch;
        this.clockMillis = clockMillis;
    }

    public Order placeOrder(String customerId, Location deliveryLocation, String restaurantId, long totalCents) {
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant == null) {
            throw new IllegalArgumentException("Unknown restaurant: " + restaurantId);
        }
        Order order = new Order("o" + nextOrderId.getAndIncrement(), customerId, deliveryLocation, restaurant,
                totalCents, clockMillis.getAsLong());
        ordersById.put(order.id(), order);
        return order;
    }

    public void subscribe(String orderId, Consumer<StatusChange> listener) {
        subscribersByOrder.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Restaurant accepts → dispatch a courier right away, so they arrive about when the food is ready.
     * If nobody is free, the order waits and {@link #dispatchWaitingOrders()} retries.
     */
    public void restaurantAccepts(String orderId) {
        Order order = order(orderId);
        transition(order, OrderStatus.ACCEPTED);
        tryAssignCourier(order);
    }

    public void restaurantRejects(String orderId) {
        transition(order(orderId), OrderStatus.CANCELLED);
    }

    public void startPreparing(String orderId) {
        transition(order(orderId), OrderStatus.PREPARING);
    }

    public void foodReady(String orderId) {
        transition(order(orderId), OrderStatus.READY_FOR_PICKUP);
    }

    public void pickedUp(String orderId) {
        Order order = order(orderId);
        if (order.courierId() == null) {
            throw new IllegalStateException("No courier assigned to " + orderId);
        }
        transition(order, OrderStatus.PICKED_UP);
    }

    public void delivered(String orderId) {
        Order order = order(orderId);
        transition(order, OrderStatus.DELIVERED);
        dispatch.release(order.courierId(), order.deliveryLocation());
    }

    /** Allowed only before cooking starts (the state machine enforces this). Frees the courier. */
    public void customerCancels(String orderId) {
        Order order = order(orderId);
        transition(order, OrderStatus.CANCELLED);
        if (order.courierId() != null) {
            dispatch.release(order.courierId(), dispatch.locationOf(order.courierId()));
        }
    }

    /** Retries courier assignment for accepted orders that are still waiting. Returns how many were assigned. */
    public int dispatchWaitingOrders() {
        int assigned = 0;
        for (Order order : ordersById.values()) {
            boolean waiting = order.courierId() == null
                    && (order.status() == OrderStatus.ACCEPTED || order.status() == OrderStatus.PREPARING
                    || order.status() == OrderStatus.READY_FOR_PICKUP);
            if (waiting && tryAssignCourier(order)) {
                assigned++;
            }
        }
        return assigned;
    }

    /** Minutes until delivery, if a courier is assigned and the order is still in progress. */
    public OptionalLong etaMinutes(String orderId) {
        Order order = order(orderId);
        if (order.courierId() == null || order.status() == OrderStatus.DELIVERED || order.status() == OrderStatus.CANCELLED) {
            return OptionalLong.empty();
        }
        Restaurant restaurant = order.restaurant();
        return OptionalLong.of(EtaCalculator.etaMinutes(restaurant.prepMinutes(),
                dispatch.locationOf(order.courierId()), restaurant.location(), order.deliveryLocation()));
    }

    public Order order(String orderId) {
        Order order = ordersById.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Unknown order: " + orderId);
        }
        return order;
    }

    private boolean tryAssignCourier(Order order) {
        return dispatch.assignNearest(order.restaurant().location())
                .map(courierId -> {
                    order.assignCourier(courierId);
                    return true;
                })
                .orElse(false);
    }

    private void transition(Order order, OrderStatus next) {
        StatusChange change = order.moveTo(next, clockMillis.getAsLong());
        subscribersByOrder.getOrDefault(order.id(), List.of()).forEach(listener -> listener.accept(change));
    }
}
