# Payment System: Design Summary

## Problem Statement
Merchants need to charge customers' cards and occasionally refund them. The money actually moves through an external payment provider over an unreliable network. The system must never double-charge, never lose track of money, and always be able to prove where every rupee went.

## Functional Requirements
1. Create a payment (charge a tokenized card) for a merchant.
2. Refund a payment, fully or partially.
3. Report payment status, including "still processing".
4. Keep an auditable ledger of every money movement.
5. Reconcile our records with the provider's.

## Non-Functional Requirements
- **Correctness over availability:** a delayed payment is acceptable; a double charge or lost payment isn't.
- **Exactly-once effect** from the client's view, despite retries (idempotency).
- **Auditability:** immutable history; every balance explainable.
- **Security:** PCI-DSS (never store raw card numbers; tokens only), encryption, strict access control.
- **Scale (assumed):** 1M payments/day (~12/s average, 10× at peaks). Modest throughput; the difficulty is correctness, not volume.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
1. **Claim the idempotency key** (unique constraint). A retry with the same key and body returns the original payment; a different body is rejected (422).
2. **Record the payment as PROCESSING** before calling out, so there's always a record of the attempt.
3. **Charge the provider,** passing our payment ID as the provider's idempotency key, so our retries are safe at the provider too.
4. **On approval:** mark SUCCEEDED and post **double-entry ledger** entries. **On decline:** FAILED, no entries. **On timeout:** leave PROCESSING. The outcome is unknown, and guessing could lose money or double-charge.
5. **Reconciliation** compares our payments with the provider's records (API + daily settlement file): resolves PROCESSING payments and flags any mismatches for humans.
6. Status changes go through a **transactional outbox** to Kafka for merchant webhooks and receipts.

## API Design
```http
POST /api/payments                   Idempotency-Key: order-77
{ "merchantId": "m1", "amountCents": 50000, "currency": "INR", "cardToken": "tok_visa" }
→ 201 SUCCEEDED | 402 FAILED (declined) | 202 PROCESSING (outcome unknown; poll or wait for webhook)
→ 422 if the key was used with a different body; 400 if the header is missing

POST /api/payments/{id}/refunds      { "amountCents": 20000 }   → 200 PARTIALLY_REFUNDED | REFUNDED; 409 if not refundable
GET  /api/payments/{id}
```

## Data Model
| Table | Key columns | Notes |
|---|---|---|
| `idempotency_keys` | `key` PK, `request_hash`, `payment_id`, `created_at` | Expire after ~24 h |
| `payments` | `id`, `merchant_id`, `amount`, `currency`, `status`, `refunded`, `psp_reference`, `version` | State machine; optimistic locking |
| `ledger_entries` | `transaction_id`, `account`, `debit`, `credit`, `created_at` | **Append-only**; sum(debit) = sum(credit) per transaction |
| `outbox` | `id`, `event`, `published` | Written in the same DB transaction as the state change |

**Ledger example.** A ₹500 charge, then a ₹200 refund:

| Transaction | Account | Debit | Credit |
|---|---|---|---|
| charge | psp_receivable | 50,000 | |
| charge | merchant_payable:m1 | | 50,000 |
| refund | merchant_payable:m1 | 20,000 | |
| refund | psp_receivable | | 20,000 |

## Key Design Decisions
| Decision | Why |
|---|---|
| **Idempotency keys + request fingerprint** | Clients retry on timeouts; the key makes retries safe, and the fingerprint catches clients reusing keys by mistake. |
| **PROCESSING as a first-class state** | A timeout isn't a failure. Marking it failed might lead the customer to pay again while the first charge actually succeeded. |
| **Provider-side idempotency (payment ID)** | Our own retries to the provider can't double-charge either. |
| **Double-entry ledger, append-only** | Money is only moved, never created; corrections are new reversing entries; the invariant is checkable at any time. |
| **Integer minor units (paise)** | Floating point can't represent 0.10 exactly; rounding errors in money are unacceptable. |
| **Reconciliation** | The provider is the source of truth for what really happened; regular comparison catches bugs, lost webhooks, and timeouts. |
| **Transactional outbox** | Publishing an event and updating the DB can't be atomic across systems; writing the event to an outbox table in the same transaction guarantees it's eventually published. |

## Bottlenecks and Trade-offs
- **Provider latency and outages:** circuit breakers, timeouts, maybe routing to a backup provider (with care to avoid double charges across providers).
- **Unknown outcomes:** the customer may see "processing" for minutes; communicate clearly and resolve quickly via webhooks and reconciliation.
- **Hot merchant accounts:** one merchant's ledger account receives every write. Use per-shard sub-accounts or batch postings.
- **Strong consistency limits scale:** shard by merchant; payment volume per shard is modest, so this works well.
- **Idempotency key retention:** keys expire (24 h); a retry after expiry is treated as a new payment, so document the window.

## Scaling Strategy
1. **One service + PostgreSQL**, all writes in transactions (this repo's shape, persisted).
2. **Webhooks + reconciliation jobs;** transactional outbox → Kafka for downstream consumers.
3. **Shard by merchant ID;** a separate ledger service with its own DB; read replicas for reporting.
4. **Multiple providers** with routing (cost, success rate, region), each with idempotent integration.
5. **Multi-region:** payments homed in one region per merchant; ledger replicated for disaster recovery.
