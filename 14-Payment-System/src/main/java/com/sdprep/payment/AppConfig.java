package com.sdprep.payment;

import com.sdprep.payment.core.FakePaymentProvider;
import com.sdprep.payment.core.Ledger;
import com.sdprep.payment.core.PaymentProvider;
import com.sdprep.payment.core.PaymentService;
import com.sdprep.payment.core.ReconciliationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the plain-Java core into Spring. */
@Configuration
public class AppConfig {

    @Bean
    PaymentProvider paymentProvider() {
        return new FakePaymentProvider(); // production: Stripe/Adyen client
    }

    @Bean
    Ledger ledger() {
        return new Ledger(); // production: append-only table in PostgreSQL
    }

    @Bean
    PaymentService paymentService(PaymentProvider provider, Ledger ledger) {
        return new PaymentService(provider, ledger);
    }

    @Bean
    ReconciliationService reconciliationService(PaymentService payments, PaymentProvider provider) {
        return new ReconciliationService(payments, provider);
    }
}
