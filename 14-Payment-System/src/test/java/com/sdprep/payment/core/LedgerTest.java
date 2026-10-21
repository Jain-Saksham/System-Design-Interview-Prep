package com.sdprep.payment.core;

import com.sdprep.payment.core.Ledger.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {

    private final Ledger ledger = new Ledger();

    @Test
    void balancedTransactionsUpdateAccountBalances() {
        ledger.post("t1", "charge", List.of(Entry.debit("psp_receivable", 1000), Entry.credit("merchant", 1000)));
        ledger.post("t2", "refund", List.of(Entry.debit("merchant", 300), Entry.credit("psp_receivable", 300)));

        assertEquals(Map.of("merchant", -700L, "psp_receivable", 700L), ledger.balances());
        assertTrue(ledger.isBalanced());
    }

    @Test
    void unbalancedTransactionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ledger.post("bad", "oops",
                List.of(Entry.debit("psp_receivable", 1000), Entry.credit("merchant", 999))));
        assertTrue(ledger.transactions().isEmpty());
    }

    @Test
    void transactionsCanHaveMoreThanTwoLegs() {
        // Charge 1000 with a 20 fee: the merchant gets 980, the platform keeps 20.
        ledger.post("t1", "charge with fee", List.of(
                Entry.debit("psp_receivable", 1000), Entry.credit("merchant", 980), Entry.credit("fees", 20)));

        assertTrue(ledger.isBalanced());
    }
}
