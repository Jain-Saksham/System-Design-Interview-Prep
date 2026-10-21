package com.sdprep.ecommerce.api;

import com.sdprep.ecommerce.core.CartService;
import com.sdprep.ecommerce.core.Catalog;
import com.sdprep.ecommerce.core.CheckoutService;
import com.sdprep.ecommerce.core.Inventory;
import com.sdprep.ecommerce.core.NotFoundException;
import com.sdprep.ecommerce.core.Order;
import com.sdprep.ecommerce.core.Product;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class StoreController {

    public record AddItemRequest(String productId, int quantity) {
    }

    public record CartView(Map<String, Integer> items, long totalCents) {
    }

    public record CheckoutRequest(String userId) {
    }

    private final Catalog catalog;
    private final Inventory inventory;
    private final CartService carts;
    private final CheckoutService checkout;

    public StoreController(Catalog catalog, Inventory inventory, CartService carts, CheckoutService checkout) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.carts = carts;
        this.checkout = checkout;
    }

    @GetMapping("/api/products")
    public List<Product> products() {
        return catalog.all();
    }

    @GetMapping("/api/inventory")
    public Map<String, Inventory.StockLevel> inventory() {
        return inventory.snapshot();
    }

    @PostMapping("/api/carts/{userId}/items")
    public CartView addToCart(@PathVariable String userId, @RequestBody AddItemRequest request) {
        Map<String, Integer> cart = carts.addItem(userId, request.productId(), request.quantity());
        return new CartView(cart, carts.totalCents(cart));
    }

    @GetMapping("/api/carts/{userId}")
    public CartView cart(@PathVariable String userId) {
        Map<String, Integer> cart = carts.view(userId);
        return new CartView(cart, carts.totalCents(cart));
    }

    /** 201 when paid; 402 Payment Required when the charge was declined (stock is released, cart kept). */
    @PostMapping("/api/orders")
    public ResponseEntity<Order> placeOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                            @RequestBody CheckoutRequest request) {
        Order order = checkout.checkout(request.userId(), idempotencyKey);
        HttpStatus status = order.status() == Order.Status.PAID ? HttpStatus.CREATED : HttpStatus.PAYMENT_REQUIRED;
        return ResponseEntity.status(status).body(order);
    }

    @GetMapping("/api/orders/{orderId}")
    public Order order(@PathVariable String orderId) {
        return checkout.find(orderId).orElseThrow(() -> new NotFoundException("Unknown order: " + orderId));
    }
}
