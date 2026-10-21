package com.sdprep.payment.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentServiceTest {

    private FakePaymentProvider provider;
    private Ledger ledger;
    private PaymentService payments;
    private ReconciliationService reconciliation;

    @BeforeEach
    void setUp() {
        provider = new FakePaymentProvider();
        ledger = new Ledger();
        payments = new PaymentService(provider, ledger);
        reconciliation = new ReconciliationService(payments, provider);
    }

    @Test
    void approvedPaymentSucceedsAndPostsBalancedLedgerEntries() {
        Payment payment = payments.createPayment("k1", request(50_000, "tok_visa"));

        assertEquals(Payment.Status.SUCCEEDED, payment.status());
        assertEquals(Map.of("psp_receivable", 50_000L, "merchant_payable:m1", -50_000L), ledger.balances());
        assertTrue(ledger.isBalanced());
    }

    @Test
    void declinedPaymentFailsWithNoLedgerEntries() {
        Payment payment = payments.createPayment("k1", request(50_000, "tok_declined"));

        assertEquals(Payment.Status.FAILED, payment.status());
        assertEquals("insufficient_funds", payment.failureReason());
        assertTrue(ledger.transactions().isEmpty());
    }

    @Test
    void retryWithSameKeyReturnsSamePaymentAndChargesOnce() {
        Payment first = payments.createPayment("k1", request(50_000, "tok_visa"));
        Payment retry = payments.createPayment("k1", request(50_000, "tok_visa"));

        assertEquals(first.id(), retry.id());
        assertEquals(1, provider.cardCharges());
        assertEquals(1, ledger.transactions().size());
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        payments.createPayment("k1", request(50_000, "tok_visa"));

        assertThrows(IdempotencyConflictException.class, () -> payments.createPayment("k1", request(99_000, "tok_visa")));
    }

    @Test
    void concurrentRetriesWithSameKeyChargeOnlyOnce() throws InterruptedException {
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 50; i++) {
                pool.submit(() -> payments.createPayment("same-key", request(10_000, "tok_visa")));
            }
        }

        assertEquals(1, provider.cardCharges());
        assertEquals(1, payments.allPayments().size());
    }

    @Test
    void partialThenFullRefundUpdatesStatusAndReversesLedger() {
        Payment payment = payments.createPayment("k1", request(50_000, "tok_visa"));

        assertEquals(Payment.Status.PARTIALLY_REFUNDED, payments.refund(payment.id(), 20_000).status());
        Payment fullyRefunded = payments.refund(payment.id(), 30_000);

        assertEquals(Payment.Status.REFUNDED, fullyRefunded.status());
        assertEquals(Map.of("psp_receivable", 0L, "merchant_payable:m1", 0L), ledger.balances());
    }

    @Test
    void cannotRefundMoreThanRemainingOrRefundAFailedPayment() {
        Payment paid = payments.createPayment("k1", request(50_000, "tok_visa"));
        payments.refund(paid.id(), 40_000);
        Payment declined = payments.createPayment("k2", request(50_000, "tok_declined"));

        assertThrows(InvalidRequestException.class, () -> payments.refund(paid.id(), 10_001));
        assertThrows(InvalidStateException.class, () -> payments.refund(declined.id(), 100));
    }

    @Test
    void timeoutLeavesPaymentProcessingUntilReconciliationFindsTheCharge() {
        Payment payment = payments.createPayment("k1", request(50_000, "tok_timeout"));
        assertEquals(Payment.Status.PROCESSING, payment.status());
        assertTrue(ledger.transactions().isEmpty()); // not confirmed yet: nothing booked

        ReconciliationService.Report report = reconciliation.run();

        assertEquals(List.of(payment.id()), report.resolvedAsSucceeded());
        assertEquals(Payment.Status.SUCCEEDED, payments.get(payment.id()).status());
        assertEquals(1, ledger.transactions().size());
    }

    @Test
    void reconciliationFailsPaymentsTheProviderNeverSaw() {
        Payment payment = payments.createPayment("k1", request(50_000, "tok_timeout_lost"));

        ReconciliationService.Report report = reconciliation.run();

        assertEquals(List.of(payment.id()), report.resolvedAsFailed());
        assertEquals(Payment.Status.FAILED, payments.get(payment.id()).status());
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(InvalidRequestException.class, () -> payments.createPayment("k", request(0, "tok_visa")));
        assertThrows(InvalidRequestException.class,
                () -> payments.createPayment("k", new CreatePaymentRequest("m1", 100, "rupees", "tok_visa")));
    }

    private static CreatePaymentRequest request(long amountCents, String cardToken) {
        return new CreatePaymentRequest("m1", amountCents, "INR", cardToken);
    }
}
