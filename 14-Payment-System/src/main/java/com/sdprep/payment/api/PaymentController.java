package com.sdprep.payment.api;

import com.sdprep.payment.core.CreatePaymentRequest;
import com.sdprep.payment.core.Ledger;
import com.sdprep.payment.core.Payment;
import com.sdprep.payment.core.PaymentService;
import com.sdprep.payment.core.ReconciliationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentController {

    public record RefundRequest(long amountCents) {
    }

    public record LedgerView(Map<String, Long> balances, boolean balanced, int transactions) {
    }

    private final PaymentService payments;
    private final ReconciliationService reconciliation;
    private final Ledger ledger;

    public PaymentController(PaymentService payments, ReconciliationService reconciliation, Ledger ledger) {
        this.payments = payments;
        this.reconciliation = reconciliation;
        this.ledger = ledger;
    }

    /** 201 succeeded · 402 declined · 202 outcome unknown (provider timeout; poll GET until resolved). */
    @PostMapping("/api/payments")
    public ResponseEntity<Payment> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody CreatePaymentRequest request) {
        Payment payment = payments.createPayment(idempotencyKey, request);
        HttpStatus status = switch (payment.status()) {
            case SUCCEEDED, PARTIALLY_REFUNDED, REFUNDED -> HttpStatus.CREATED;
            case FAILED -> HttpStatus.PAYMENT_REQUIRED;
            case PROCESSING -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(payment);
    }

    @GetMapping("/api/payments/{id}")
    public Payment get(@PathVariable String id) {
        return payments.get(id);
    }

    @PostMapping("/api/payments/{id}/refunds")
    public Payment refund(@PathVariable String id, @RequestBody RefundRequest request) {
        return payments.refund(id, request.amountCents());
    }

    @GetMapping("/api/ledger")
    public LedgerView ledger() {
        return new LedgerView(ledger.balances(), ledger.isBalanced(), ledger.transactions().size());
    }

    @PostMapping("/api/reconciliation")
    public ReconciliationService.Report reconcile() {
        return reconciliation.run();
    }
}
