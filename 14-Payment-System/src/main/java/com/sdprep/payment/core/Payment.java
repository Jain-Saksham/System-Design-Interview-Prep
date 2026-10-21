package com.sdprep.payment.core;

/**
 * A payment's current state. Immutable: every change produces a new copy, which keeps updates explicit.
 *
 * @param pspReference the payment provider's ID for this charge (null until the provider confirms)
 */
public record Payment(String id, String merchantId, long amountCents, String currency, Status status,
                      long refundedCents, String pspReference, String failureReason) {

    public enum Status {
        PROCESSING,          // sent to the provider; outcome not known yet (or the response was lost)
        SUCCEEDED,
        FAILED,
        PARTIALLY_REFUNDED,
        REFUNDED
    }

    static Payment processing(String id, String merchantId, long amountCents, String currency) {
        return new Payment(id, merchantId, amountCents, currency, Status.PROCESSING, 0, null, null);
    }

    Payment succeeded(String pspReference) {
        return new Payment(id, merchantId, amountCents, currency, Status.SUCCEEDED, 0, pspReference, null);
    }

    Payment failed(String reason) {
        return new Payment(id, merchantId, amountCents, currency, Status.FAILED, 0, null, reason);
    }

    Payment refunded(long refundCents) {
        long totalRefunded = refundedCents + refundCents;
        Status newStatus = totalRefunded == amountCents ? Status.REFUNDED : Status.PARTIALLY_REFUNDED;
        return new Payment(id, merchantId, amountCents, currency, newStatus, totalRefunded, pspReference, null);
    }

    long refundableCents() {
        return amountCents - refundedCents;
    }
}
