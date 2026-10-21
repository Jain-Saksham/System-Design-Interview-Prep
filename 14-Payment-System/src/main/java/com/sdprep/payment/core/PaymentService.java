package com.sdprep.payment.core;

import com.sdprep.payment.core.Ledger.Entry;
import com.sdprep.payment.core.PaymentProvider.ChargeResult;
import com.sdprep.payment.core.PaymentProvider.Outcome;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accepts payments, talks to the provider, records money movements in the ledger, and handles refunds.
 *
 * <p>The three rules that keep money safe:
 * <ol>
 *   <li><b>Idempotency:</b> the same Idempotency-Key never creates a second payment.</li>
 *   <li><b>Unknown outcomes stay unknown:</b> a provider timeout leaves the payment PROCESSING;
 *       reconciliation later finds out what really happened. Guessing "failed" could lose money.</li>
 *   <li><b>Ledger only on confirmed movements:</b> entries are posted when the provider confirms.</li>
 * </ol>
 */
public class PaymentService {

    static final String PSP_RECEIVABLE = "psp_receivable";

    private record IdempotencyRecord(CreatePaymentRequest request, String paymentId) {
    }

    private final PaymentProvider provider;
    private final Ledger ledger;
    private final Map<String, Payment> paymentsById = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyKeys = new ConcurrentHashMap<>(); // production: unique DB key
    private final Map<String, Object> locksByPayment = new ConcurrentHashMap<>();
    private final AtomicLong nextPaymentId = new AtomicLong(1);

    public PaymentService(PaymentProvider provider, Ledger ledger) {
        this.provider = provider;
        this.ledger = ledger;
    }

    public Payment createPayment(String idempotencyKey, CreatePaymentRequest request) {
        request.validate();
        AtomicBoolean firstRequestForKey = new AtomicBoolean(false);
        // Atomic: registers the key AND saves a PROCESSING payment together, so a concurrent retry
        // always finds the payment.
        IdempotencyRecord record = idempotencyKeys.computeIfAbsent(idempotencyKey, key -> {
            firstRequestForKey.set(true);
            Payment payment = save(Payment.processing("pay_" + nextPaymentId.getAndIncrement(),
                    request.merchantId(), request.amountCents(), request.currency()));
            return new IdempotencyRecord(request, payment.id());
        });

        if (!firstRequestForKey.get()) {
            // A retry. Same body → return the original; different body → client bug, refuse loudly.
            if (!record.request().equals(request)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return get(record.paymentId()); // may still be PROCESSING if the first request is in flight
        }
        return chargeWithProvider(record.paymentId(), request);
    }

    private Payment chargeWithProvider(String paymentId, CreatePaymentRequest request) {
        synchronized (lockFor(paymentId)) {
            try {
                ChargeResult result = provider.charge(paymentId, request.cardToken(), request.amountCents());
                return result.outcome() == Outcome.APPROVED
                        ? markSucceeded(paymentId, result.pspReference())
                        : save(get(paymentId).failed(result.declineReason()));
            } catch (PaymentProvider.TimeoutException e) {
                return get(paymentId); // still PROCESSING: reconciliation will resolve it
            }
        }
    }

    public Payment refund(String paymentId, long amountCents) {
        synchronized (lockFor(paymentId)) {
            Payment payment = get(paymentId);
            boolean refundable = payment.status() == Payment.Status.SUCCEEDED
                    || payment.status() == Payment.Status.PARTIALLY_REFUNDED;
            if (!refundable) {
                throw new InvalidStateException("Cannot refund a payment that is " + payment.status());
            }
            if (amountCents <= 0 || amountCents > payment.refundableCents()) {
                throw new InvalidRequestException("Refund must be between 1 and " + payment.refundableCents() + " cents");
            }
            provider.refund(payment.pspReference(), amountCents);
            // Reverse the original movement for the refunded amount.
            ledger.post(paymentId + ":refund:" + (payment.refundedCents() + amountCents), "Refund " + paymentId, List.of(
                    Entry.debit(merchantAccount(payment), amountCents),
                    Entry.credit(PSP_RECEIVABLE, amountCents)));
            return save(payment.refunded(amountCents));
        }
    }

    public Payment get(String paymentId) {
        return find(paymentId).orElseThrow(() -> new NotFoundException("Unknown payment: " + paymentId));
    }

    public Optional<Payment> find(String paymentId) {
        return Optional.ofNullable(paymentsById.get(paymentId));
    }

    List<Payment> allPayments() {
        return List.copyOf(paymentsById.values());
    }

    /** Used by reconciliation when the provider confirms a charge we never heard back about. */
    Payment markSucceeded(String paymentId, String pspReference) {
        synchronized (lockFor(paymentId)) {
            Payment payment = get(paymentId).succeeded(pspReference);
            // Money is now owed to us by the PSP, and we owe it to the merchant.
            ledger.post(paymentId + ":charge", "Charge " + paymentId, List.of(
                    Entry.debit(PSP_RECEIVABLE, payment.amountCents()),
                    Entry.credit(merchantAccount(payment), payment.amountCents())));
            return save(payment);
        }
    }

    /** Used by reconciliation when the provider has no record of a charge. */
    Payment markFailed(String paymentId, String reason) {
        synchronized (lockFor(paymentId)) {
            return save(get(paymentId).failed(reason));
        }
    }

    private Payment save(Payment payment) {
        paymentsById.put(payment.id(), payment);
        return payment;
    }

    private Object lockFor(String paymentId) {
        return locksByPayment.computeIfAbsent(paymentId, id -> new Object());
    }

    private static String merchantAccount(Payment payment) {
        return "merchant_payable:" + payment.merchantId();
    }
}
