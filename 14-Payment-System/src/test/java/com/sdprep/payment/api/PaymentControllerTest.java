package com.sdprep.payment.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP: status codes, idempotency header, refunds, ledger, reconciliation. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentControllerTest {

    private static final String BODY = """
            {"merchantId":"m1","amountCents":50000,"currency":"INR","cardToken":"%s"}""";

    @Autowired
    private MockMvc mvc;

    @Test
    void createReplayRefundAndCheckLedger() throws Exception {
        pay("k1", "tok_visa").andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("pay_1"));
        pay("k1", "tok_visa").andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("pay_1"));

        mvc.perform(post("/api/payments/pay_1/refunds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":20000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));

        mvc.perform(get("/api/ledger"))
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.balances.psp_receivable").value(30000));
    }

    @Test
    void declinedIs402TimeoutIs202AndReconciliationResolvesIt() throws Exception {
        pay("k1", "tok_declined").andExpect(status().isPaymentRequired());
        pay("k2", "tok_timeout").andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PROCESSING"));

        mvc.perform(post("/api/reconciliation")).andExpect(jsonPath("$.resolvedAsSucceeded[0]").value("pay_2"));
        mvc.perform(get("/api/payments/pay_2")).andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void errorCases() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("tok_visa")))
                .andExpect(status().isBadRequest()); // no Idempotency-Key
        pay("k1", "tok_visa");
        mvc.perform(post("/api/payments").header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("tok_other")))
                .andExpect(status().isUnprocessableEntity()); // same key, different body
        mvc.perform(get("/api/payments/nope")).andExpect(status().isNotFound());
    }

    private ResultActions pay(String key, String token) throws Exception {
        return mvc.perform(post("/api/payments").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(token)));
    }
}
