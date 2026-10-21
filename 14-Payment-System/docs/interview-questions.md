# Payment System: Interview Questions

**1. How do you guarantee a customer is never charged twice?**
Idempotency at two levels. Clients send an `Idempotency-Key`; the server claims it with a unique constraint and returns the original result for retries. The server also passes its own payment ID as the provider's idempotency key, so even our retries to the provider can't double-charge.

**2. What should happen if the same idempotency key arrives with a different request body?**
Reject with 422. It's a client bug (key reuse), and silently returning the old result or processing the new one would both be wrong. Store a hash of the request with the key to detect it.

**3. The provider call times out. Did the payment succeed?**
Unknown. Keep the payment PROCESSING and return 202. Never assume failure, or the customer may pay twice. Resolve it via the provider's webhook, a status lookup, or reconciliation. Only then mark SUCCEEDED or FAILED.

**4. Why double-entry bookkeeping?**
Every transaction moves money between accounts, with total debits equal to total credits. Money can't appear or vanish because of a bug, every balance is the sum of its entries, and the system-wide invariant (all balances sum to zero) is checkable at any moment. Corrections are new reversing entries, never edits.

**5. Why store amounts as integers?**
Floating point can't represent most decimals exactly (0.1 + 0.2 ≠ 0.3). Store minor units (paise, cents) as integers, plus a currency code.

**6. What is reconciliation, and why is it necessary?**
Periodically comparing our records with the provider's (API and daily settlement files). It catches lost webhooks, timeouts, bugs, and fraud: PROCESSING payments get resolved, and amount or refund mismatches are flagged for investigation. Never auto-correct money silently.

**7. How do you publish a "payment succeeded" event reliably?**
Transactional outbox: in the same DB transaction that updates the payment, insert an event row into an `outbox` table. A relay process publishes outbox rows to Kafka and marks them sent. Consumers must be idempotent, since delivery is at-least-once.

**8. How do refunds work, and what can go wrong?**
Validate the payment is refundable and the amount ≤ remaining; call the provider's refund; post reversing ledger entries; update status (PARTIALLY_REFUNDED or REFUNDED). Risks: double refunds on retry (use a refund idempotency key) and concurrent refunds exceeding the total (lock the payment row or use optimistic locking).

**9. How do you keep card data secure?**
Use the provider's client SDK to tokenize cards in the browser or app, so raw card numbers never touch your servers (most PCI-DSS scope avoided). Encrypt everything at rest, restrict access, audit all operations.

**10. Why not use a distributed transaction between our DB and the provider?**
External providers don't participate in 2PC, and 2PC blocks on failures. Use the state machine (PROCESSING), idempotency, webhooks, and reconciliation instead: eventual consistency with guaranteed convergence.

**11. How would you scale the ledger?**
Shard by merchant (or account). Avoid hot rows: don't update a running balance on every entry; compute balances from entries, or keep periodic snapshots plus deltas. Batch postings for very hot accounts.

**12. Authorization vs capture?**
Authorization reserves funds on the card; capture actually moves them (often at shipment). Two-step flows reduce refunds for cancelled orders but add state (authorizations expire after ~7 days). This repo uses a single-step charge for simplicity.

**Likely follow-ups:** chargebacks and disputes · paying out to merchants (settlement + bank transfers) · fraud detection (risk scoring before charging) · supporting multiple currencies (FX rates locked at payment time).
