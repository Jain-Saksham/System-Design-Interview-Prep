package com.sdprep.payment.core;

import java.util.List;

/** The external payment service provider (PSP): Stripe, Adyen, Razorpay... */
public interface PaymentProvider {

    enum Outcome { APPROVED, DECLINED }

    record ChargeResult(Outcome outcome, String pspReference, String declineReason) {
    }

    /** What the provider's own records say happened, used for reconciliation. */
    record ProviderRecord(String paymentId, String pspReference, long chargedCents, long refundedCents) {
    }

    /** The request timed out: the charge may or may not have happened. */
    class TimeoutException extends RuntimeException {
        public TimeoutException(String paymentId) {
            super("Provider timed out for " + paymentId);
        }
    }

    /**
     * Charges a card. {@code paymentId} is sent as the provider's idempotency key, so retrying
     * the same payment never charges twice at the provider either.
     */
    ChargeResult charge(String paymentId, String cardToken, long amountCents);

    void refund(String pspReference, long amountCents);

    List<ProviderRecord> records();
}
