package com.sdprep.ecommerce.core;

public class OutOfStockException extends RuntimeException {
    public OutOfStockException(String productId) {
        super("Not enough stock for " + productId);
    }
}
