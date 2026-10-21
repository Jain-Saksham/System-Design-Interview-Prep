package com.sdprep.ecommerce.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shopping carts: product → quantity per user. Adding to a cart does NOT reserve stock;
 * stock is only checked and held at checkout (otherwise abandoned carts would lock up inventory).
 *
 * <p>Production: Redis or DynamoDB keyed by user (fast, cheap, OK to lose occasionally).
 */
public class CartService {

    private final Catalog catalog;
    private final Map<String, Map<String, Integer>> cartsByUser = new ConcurrentHashMap<>();

    public CartService(Catalog catalog) {
        this.catalog = catalog;
    }

    public Map<String, Integer> addItem(String userId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be positive");
        }
        catalog.get(productId); // fails fast for unknown products
        Map<String, Integer> cart = cartsByUser.computeIfAbsent(userId, id -> new LinkedHashMap<>());
        synchronized (cart) {
            cart.merge(productId, quantity, Integer::sum);
            return Map.copyOf(cart);
        }
    }

    public Map<String, Integer> view(String userId) {
        Map<String, Integer> cart = cartsByUser.getOrDefault(userId, Map.of());
        synchronized (cart) {
            return Map.copyOf(cart);
        }
    }

    public void clear(String userId) {
        cartsByUser.remove(userId);
    }

    public long totalCents(Map<String, Integer> cart) {
        return cart.entrySet().stream()
                .mapToLong(item -> catalog.get(item.getKey()).priceCents() * item.getValue())
                .sum();
    }
}
