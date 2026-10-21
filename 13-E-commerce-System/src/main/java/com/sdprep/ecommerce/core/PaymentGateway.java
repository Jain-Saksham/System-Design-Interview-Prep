package com.sdprep.ecommerce.core;

/**
 * Charges the customer. Production: the payment service (see 14-Payment-System), called with the
 * order ID as the idempotency key so a retried checkout can't charge twice.
 */
@FunctionalInterface
public interface PaymentGateway {

    /** @return true if the charge succeeded, false if it was declined */
    boolean charge(String userId, long amountCents, String idempotencyKey);
}
