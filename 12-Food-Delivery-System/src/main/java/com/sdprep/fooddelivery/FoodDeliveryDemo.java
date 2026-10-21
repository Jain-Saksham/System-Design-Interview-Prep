package com.sdprep.fooddelivery;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Runnable walkthrough: order lifecycle, nearest-courier dispatch, ETA, live status events, cancellation rules. */
public class FoodDeliveryDemo {

    public static void main(String[] args) {
        AtomicLong clock = new AtomicLong();
        Restaurant pizzeria = new Restaurant("r1", "Napoli Pizza", new Location(2, 2), 15);
        DispatchService dispatch = new DispatchService();
        dispatch.courierOnline("kiran", new Location(5, 5));   // ~4.2 km from the restaurant
        dispatch.courierOnline("sam", new Location(2.5, 1.5)); // ~0.7 km
        OrderService orders = new OrderService(List.of(pizzeria), dispatch, clock::get);

        Order order = orders.placeOrder("cust-7", new Location(4, 6), "r1", 59900);
        orders.subscribe(order.id(), change -> System.out.printf("  [customer app] t=%2d min  %s%n",
                change.atMillis() / 60_000, change.status()));
        System.out.println("Order " + order.id() + " placed at " + pizzeria.name() + " (status " + order.status() + ")");

        at(clock, 1);
        orders.restaurantAccepts(order.id());
        System.out.printf("  courier assigned: %s (nearest free courier), ETA %d min%n",
                order.courierId(), orders.etaMinutes(order.id()).orElseThrow());

        at(clock, 2);
        orders.startPreparing(order.id());
        try {
            orders.customerCancels(order.id());
        } catch (IllegalStateException e) {
            System.out.println("  customer tries to cancel -> rejected: " + e.getMessage());
        }

        at(clock, 16);
        orders.foodReady(order.id());
        at(clock, 17);
        orders.pickedUp(order.id());
        at(clock, 30);
        orders.delivered(order.id());
        System.out.println("  sam is " + (dispatch.isBusy("sam") ? "busy" : "free again") + " at the customer's location");
    }

    private static void at(AtomicLong clock, long minutes) {
        clock.set(minutes * 60_000);
    }
}
