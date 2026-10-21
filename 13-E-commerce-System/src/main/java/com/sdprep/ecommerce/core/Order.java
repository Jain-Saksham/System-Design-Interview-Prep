package com.sdprep.ecommerce.core;

import java.util.List;

/**
 * A placed order. Lines copy the name and price at checkout time, so later catalog price changes
 * never alter what the customer agreed to pay.
 */
public record Order(String id, String userId, List<OrderLine> lines, long totalCents, Status status) {

    public enum Status { PAID, PAYMENT_FAILED }

    public record OrderLine(String productId, String productName, int quantity, long unitPriceCents) {
    }
}
