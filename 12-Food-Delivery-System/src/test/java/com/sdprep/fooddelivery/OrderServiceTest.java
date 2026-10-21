package com.sdprep.fooddelivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {

    private static final Restaurant RESTAURANT = new Restaurant("r1", "Test Kitchen", new Location(0, 0), 15);
    private static final Location CUSTOMER = new Location(0, 5);

    private DispatchService dispatch;
    private OrderService orders;

    @BeforeEach
    void setUp() {
        dispatch = new DispatchService();
        orders = new OrderService(List.of(RESTAURANT), dispatch, () -> 0L);
    }

    @Test
    void fullLifecyclePublishesEveryStatusInOrder() {
        dispatch.courierOnline("c1", new Location(1, 0));
        Order order = orders.placeOrder("cust", CUSTOMER, "r1", 1000);
        List<OrderStatus> seen = new ArrayList<>();
        orders.subscribe(order.id(), change -> seen.add(change.status()));

        orders.restaurantAccepts(order.id());
        orders.startPreparing(order.id());
        orders.foodReady(order.id());
        orders.pickedUp(order.id());
        orders.delivered(order.id());

        assertEquals(List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
                OrderStatus.PICKED_UP, OrderStatus.DELIVERED), seen);
        assertEquals(6, order.history().size()); // PLACED + 5 changes
    }

    @Test
    void nearestFreeCourierIsAssignedOnAcceptance() {
        dispatch.courierOnline("far", new Location(10, 10));
        dispatch.courierOnline("near", new Location(1, 1));
        Order order = orders.placeOrder("cust", CUSTOMER, "r1", 1000);

        orders.restaurantAccepts(order.id());

        assertEquals("near", order.courierId());
        assertTrue(dispatch.isBusy("near"));
    }

    @Test
    void courierIsFreedAtTheCustomerAfterDelivery() {
        dispatch.courierOnline("c1", new Location(1, 0));
        Order order = deliveredOrder();

        assertFalse(dispatch.isBusy("c1"));
        assertEquals(CUSTOMER, dispatch.locationOf("c1"));
        assertEquals(OrderStatus.DELIVERED, order.status());
    }

    @Test
    void customerCanCancelBeforeCookingAndCourierIsReleased() {
        dispatch.courierOnline("c1", new Location(1, 0));
        Order order = orders.placeOrder("cust", CUSTOMER, "r1", 1000);
        orders.restaurantAccepts(order.id());

        orders.customerCancels(order.id());

        assertEquals(OrderStatus.CANCELLED, order.status());
        assertFalse(dispatch.isBusy("c1"));
    }

    @Test
    void cancellingAfterCookingStartedIsRejected() {
        dispatch.courierOnline("c1", new Location(1, 0));
        Order order = orders.placeOrder("cust", CUSTOMER, "r1", 1000);
        orders.restaurantAccepts(order.id());
        orders.startPreparing(order.id());

        assertThrows(IllegalStateException.class, () -> orders.customerCancels(order.id()));
        assertEquals(OrderStatus.PREPARING, order.status());
    }

    @Test
    void orderWaitsForACourierWhenNoneIsFree() {
        Order order = orders.placeOrder("cust", CUSTOMER, "r1", 1000);
        orders.restaurantAccepts(order.id());
        assertNull(order.courierId());
        assertTrue(orders.etaMinutes(order.id()).isEmpty());

        dispatch.courierOnline("late", new Location(2, 0));

        assertEquals(1, orders.dispatchWaitingOrders());
        assertEquals("late", order.courierId());
    }

    @Test
    void etaIsPrepTimeOrTravelWhicheverIsLongerPlusDelivery() {
        // Courier 1 km away = 3 min (< 15 min prep); restaurant → customer 5 km = 15 min. ETA = 15 + 15.
        assertEquals(30, EtaCalculator.etaMinutes(15, new Location(1, 0), RESTAURANT.location(), CUSTOMER));
        // Courier 10 km away = 30 min (> prep). ETA = 30 + 15.
        assertEquals(45, EtaCalculator.etaMinutes(15, new Location(10, 0), RESTAURANT.location(), CUSTOMER));
    }

    @Test
    void cannotSkipStatuses() {
        dispatch.courierOnline("c1", new Location(1, 0));
        Order order = orders.placeOrder("cust", CUSTOMER, "r1", 1000);
        orders.restaurantAccepts(order.id());

        assertThrows(IllegalStateException.class, () -> orders.delivered(order.id()));
    }

    private Order deliveredOrder() {
        Order order = orders.placeOrder("cust", CUSTOMER, "r1", 1000);
        orders.restaurantAccepts(order.id());
        orders.startPreparing(order.id());
        orders.foodReady(order.id());
        orders.pickedUp(order.id());
        orders.delivered(order.id());
        return order;
    }
}
