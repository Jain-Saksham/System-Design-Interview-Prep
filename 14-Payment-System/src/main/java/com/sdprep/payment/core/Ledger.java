package com.sdprep.payment.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Double-entry bookkeeping: every movement of money is a transaction whose debits equal its credits.
 * Money is never created or destroyed by a bug, only moved between accounts, and the books can always be
 * checked: if total debits ≠ total credits, something is wrong.
 *
 * <p>Append-only: mistakes are fixed with new reversing entries, never by editing history.
 */
public class Ledger {

    public record Entry(String account, long debitCents, long creditCents) {

        public static Entry debit(String account, long cents) {
            return new Entry(account, cents, 0);
        }

        public static Entry credit(String account, long cents) {
            return new Entry(account, 0, cents);
        }
    }

    public record Transaction(String id, String description, List<Entry> entries) {
    }

    private final List<Transaction> transactions = new ArrayList<>();

    public synchronized void post(String transactionId, String description, List<Entry> entries) {
        long debits = entries.stream().mapToLong(Entry::debitCents).sum();
        long credits = entries.stream().mapToLong(Entry::creditCents).sum();
        if (debits != credits || debits == 0) {
            throw new IllegalArgumentException("Unbalanced transaction " + transactionId + ": debits "
                    + debits + " ≠ credits " + credits);
        }
        transactions.add(new Transaction(transactionId, description, List.copyOf(entries)));
    }

    /** Balance per account as debits − credits (positive: we're owed it; negative: we owe it). */
    public synchronized Map<String, Long> balances() {
        Map<String, Long> balances = new TreeMap<>();
        for (Transaction transaction : transactions) {
            for (Entry entry : transaction.entries()) {
                balances.merge(entry.account(), entry.debitCents() - entry.creditCents(), Long::sum);
            }
        }
        return balances;
    }

    /** The fundamental invariant: all balances sum to zero. */
    public synchronized boolean isBalanced() {
        return balances().values().stream().mapToLong(Long::longValue).sum() == 0;
    }

    public synchronized List<Transaction> transactions() {
        return List.copyOf(transactions);
    }
}
