package com.sdprep.payment.core;

import com.sdprep.payment.core.PaymentProvider.ProviderRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares our records with the provider's records and fixes or reports differences.
 * Production: runs on a schedule (e.g. hourly) and against the provider's daily settlement file.
 */
public class ReconciliationService {

    public record Report(List<String> resolvedAsSucceeded, List<String> resolvedAsFailed, List<String> mismatches) {
    }

    private final PaymentService payments;
    private final PaymentProvider provider;

    public ReconciliationService(PaymentService payments, PaymentProvider provider) {
        this.payments = payments;
        this.provider = provider;
    }

    public Report run() {
        Map<String, ProviderRecord> providerRecords = provider.records().stream()
                .collect(Collectors.toMap(ProviderRecord::paymentId, Function.identity()));
        List<String> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();

        for (Payment payment : payments.allPayments()) {
            ProviderRecord record = providerRecords.get(payment.id());
            if (payment.status() == Payment.Status.PROCESSING) {
                // We never learned the outcome. The provider's record is the truth.
                if (record != null) {
                    payments.markSucceeded(payment.id(), record.pspReference());
                    succeeded.add(payment.id());
                } else {
                    payments.markFailed(payment.id(), "not_found_at_provider");
                    failed.add(payment.id());
                }
            } else if (record != null && (record.chargedCents() != payment.amountCents()
                    || record.refundedCents() != payment.refundedCents())) {
                mismatches.add(payment.id()); // humans investigate; never auto-correct money silently
            }
        }
        return new Report(succeeded, failed, mismatches);
    }
}
