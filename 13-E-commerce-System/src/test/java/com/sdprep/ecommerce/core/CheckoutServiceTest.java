package com.sdprep.ecommerce.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutServiceTest {

    private Catalog catalog;
    private Inventory inventory;
    private CartService carts;
    private final AtomicInteger charges = new AtomicInteger();
    private volatile boolean paymentsSucceed = true;
    private CheckoutService checkout;

    @BeforeEach
    void setUp() {
        catalog = new Catalog();
        catalog.add(new Product("kbd", "Keyboard", 5_000));
        catalog.add(new Product("mouse", "Mouse", 1_500));
        inventory = new Inventory();
        inventory.setStock("kbd", 2);
        inventory.setStock("mouse", 10);
        carts = new CartService(catalog);
        checkout = new CheckoutService(catalog, inventory, carts, (user, amount, key) -> {
            charges.incrementAndGet();
            return paymentsSucceed;
        });
    }

    @Test
    void successfulCheckoutSellsStockAndClearsCart() {
        carts.addItem("u1", "kbd", 1);
        carts.addItem("u1", "mouse", 2);

        Order order = checkout.checkout("u1", "key-1");

        assertEquals(Order.Status.PAID, order.status());
        assertEquals(8_000, order.totalCents());
        assertEquals(new Inventory.StockLevel(1, 0), inventory.stockOf("kbd"));
        assertTrue(carts.view("u1").isEmpty());
    }

    @Test
    void failedPaymentReleasesStockAndKeepsCart() {
        paymentsSucceed = false;
        carts.addItem("u1", "kbd", 2);

        Order order = checkout.checkout("u1", "key-1");

        assertEquals(Order.Status.PAYMENT_FAILED, order.status());
        assertEquals(new Inventory.StockLevel(2, 0), inventory.stockOf("kbd"));
        assertEquals(2, carts.view("u1").get("kbd"));
    }

    @Test
    void outOfStockReservesNothingAndChargesNothing() {
        carts.addItem("u1", "mouse", 1);
        carts.addItem("u1", "kbd", 3); // only 2 in stock

        assertThrows(OutOfStockException.class, () -> checkout.checkout("u1", "key-1"));
        assertEquals(new Inventory.StockLevel(10, 0), inventory.stockOf("mouse")); // all-or-nothing
        assertEquals(0, charges.get());
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsSameOrderWithoutChargingAgain() {
        carts.addItem("u1", "kbd", 1);

        Order first = checkout.checkout("u1", "key-1");
        Order retry = checkout.checkout("u1", "key-1");

        assertEquals(first, retry);
        assertEquals(1, charges.get());
        assertEquals(1, inventory.stockOf("kbd").available());
    }

    @Test
    void orderKeepsThePriceAtCheckoutTime() {
        carts.addItem("u1", "kbd", 1);
        Order order = checkout.checkout("u1", "key-1");

        catalog.add(new Product("kbd", "Keyboard", 9_999)); // price rises later

        assertEquals(5_000, order.lines().get(0).unitPriceCents());
    }

    @Test
    void emptyCartCannotBeCheckedOut() {
        assertThrows(InvalidRequestException.class, () -> checkout.checkout("nobody", "key-1"));
    }

    @Test
    void concurrentBuyersNeverOversellTheLastUnits() throws InterruptedException {
        List<String> buyers = IntStream.range(0, 50).mapToObj(i -> "u" + i).toList();
        buyers.forEach(user -> carts.addItem(user, "kbd", 1));
        Set<String> outcomes = ConcurrentHashMap.newKeySet();

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (String user : buyers) {
                pool.submit(() -> {
                    try {
                        checkout.checkout(user, "key-" + user);
                        outcomes.add(user);
                    } catch (OutOfStockException expected) {
                        // most buyers lose the race
                    }
                });
            }
        }

        assertEquals(2, outcomes.size()); // exactly the 2 units in stock
        assertEquals(new Inventory.StockLevel(0, 0), inventory.stockOf("kbd"));
    }
}
