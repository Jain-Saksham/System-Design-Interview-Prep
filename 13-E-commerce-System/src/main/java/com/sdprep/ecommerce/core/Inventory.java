package com.sdprep.ecommerce.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Stock per product, split into available (can be sold) and reserved (held for a checkout in progress).
 *
 * <p>{@code reserve} is all-or-nothing: if any item is short, nothing is reserved. Production does this in
 * one DB transaction with conditional updates, so two buyers can never both get the last unit:
 * <pre>UPDATE inventory SET available = available - :qty, reserved = reserved + :qty
 *  WHERE product_id = :id AND available >= :qty   -- 0 rows updated = out of stock</pre>
 */
public class Inventory {

    public record StockLevel(int available, int reserved) {
    }

    private final Map<String, StockLevel> stockByProduct = new HashMap<>();

    public synchronized void setStock(String productId, int available) {
        stockByProduct.put(productId, new StockLevel(available, 0));
    }

    public synchronized StockLevel stockOf(String productId) {
        return stockByProduct.getOrDefault(productId, new StockLevel(0, 0));
    }

    /** Holds every requested quantity, or throws and holds nothing. */
    public synchronized void reserve(Map<String, Integer> quantitiesByProduct) {
        quantitiesByProduct.forEach((productId, quantity) -> {
            if (stockOf(productId).available() < quantity) {
                throw new OutOfStockException(productId); // checked for all items before changing any
            }
        });
        quantitiesByProduct.forEach((productId, quantity) -> {
            StockLevel stock = stockOf(productId);
            stockByProduct.put(productId, new StockLevel(stock.available() - quantity, stock.reserved() + quantity));
        });
    }

    /** Payment succeeded: reserved units are sold. */
    public synchronized void commit(Map<String, Integer> quantitiesByProduct) {
        quantitiesByProduct.forEach((productId, quantity) -> {
            StockLevel stock = stockOf(productId);
            stockByProduct.put(productId, new StockLevel(stock.available(), stock.reserved() - quantity));
        });
    }

    /** Payment failed or checkout abandoned: reserved units go back on sale. */
    public synchronized void release(Map<String, Integer> quantitiesByProduct) {
        quantitiesByProduct.forEach((productId, quantity) -> {
            StockLevel stock = stockOf(productId);
            stockByProduct.put(productId, new StockLevel(stock.available() + quantity, stock.reserved() - quantity));
        });
    }

    /** Snapshot for the API and tests. */
    public synchronized Map<String, StockLevel> snapshot() {
        return Map.copyOf(new HashMap<>(stockByProduct));
    }
}
