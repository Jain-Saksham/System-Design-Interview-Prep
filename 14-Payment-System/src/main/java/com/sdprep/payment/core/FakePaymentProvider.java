package com.sdprep.payment.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulated PSP. Behaviour depends on the card token:
 * <ul>
 *   <li>{@code tok_visa}: approved</li>
 *   <li>{@code tok_declined}: declined (insufficient funds)</li>
 *   <li>{@code tok_timeout}: the card IS charged, but the response is lost (we see a timeout)</li>
 *   <li>{@code tok_timeout_lost}: the request never reached the provider (timeout, nothing charged)</li>
 * </ul>
 */
public class FakePaymentProvider implements PaymentProvider {

    private final Map<String, ProviderRecord> recordsByPaymentId = new LinkedHashMap<>();
    private final Map<String, ChargeResult> resultsByPaymentId = new LinkedHashMap<>();
    private final AtomicInteger cardCharges = new AtomicInteger();
    private int nextReference = 1;

    @Override
    public synchronized ChargeResult charge(String paymentId, String cardToken, long amountCents) {
        ChargeResult previous = resultsByPaymentId.get(paymentId);
        if (previous != null) {
            return previous; // provider-side idempotency: same payment ID, same answer, no second charge
        }
        switch (cardToken) {
            case "tok_declined" -> {
                ChargeResult declined = new ChargeResult(Outcome.DECLINED, null, "insufficient_funds");
                resultsByPaymentId.put(paymentId, declined);
                return declined;
            }
            case "tok_timeout_lost" -> throw new TimeoutException(paymentId);
            case "tok_timeout" -> {
                approve(paymentId, amountCents);  // money moves...
                throw new TimeoutException(paymentId); // ...but our side never hears about it
            }
            default -> {
                return approve(paymentId, amountCents);
            }
        }
    }

    @Override
    public synchronized void refund(String pspReference, long amountCents) {
        recordsByPaymentId.replaceAll((paymentId, record) -> record.pspReference().equals(pspReference)
                ? new ProviderRecord(paymentId, pspReference, record.chargedCents(), record.refundedCents() + amountCents)
                : record);
    }

    @Override
    public synchronized List<ProviderRecord> records() {
        return new ArrayList<>(recordsByPaymentId.values());
    }

    /** How many times a card was actually charged (tests use this to prove no double charges). */
    public int cardCharges() {
        return cardCharges.get();
    }

    private ChargeResult approve(String paymentId, long amountCents) {
        String reference = "psp_" + nextReference++;
        cardCharges.incrementAndGet();
        recordsByPaymentId.put(paymentId, new ProviderRecord(paymentId, reference, amountCents, 0));
        ChargeResult approved = new ChargeResult(Outcome.APPROVED, reference, null);
        resultsByPaymentId.put(paymentId, approved);
        return approved;
    }
}
