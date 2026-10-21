# E-commerce System: Interview Questions

**1. Estimate the traffic split.**
Assume 50M DAU × 20 page views = 1B catalog reads/day ≈ **12k reads/s** average (much higher at peak). With 1M orders/day ≈ **12 checkouts/s** average, but a flash sale can spike to thousands per second. Reads are cached aggressively; checkout is engineered for contention.

**2. How do you prevent overselling?**
Make check-and-decrement atomic in the database: `UPDATE inventory SET available = available - 1 WHERE id = ? AND available >= 1`, and treat 0 updated rows as out of stock. Never read stock in application code and then write it back; that's a race. For multiple items, do all updates in one transaction and roll back if any fails.

**3. When do you reserve stock: add-to-cart or checkout?**
Usually at checkout. Reserving at add-to-cart lets abandoned carts lock inventory. Exceptions: limited-quantity drops or ticketing, which reserve for a short window (e.g., 10 minutes) with expiry (see [15-Ticket-Booking](../../15-Ticket-Booking-System/README.md)).

**4. Inventory and payment are separate services. How do you keep them consistent?**
A saga: reserve stock → charge → confirm, with compensating actions (release stock, refund) when a later step fails. Track saga state durably so a crash mid-way can be resumed or compensated. Distributed two-phase commit is avoided because it's slow and blocks on failures.

**5. A user double-clicks "Place order". What happens?**
The client sends the same `Idempotency-Key` for both requests. The server stores the key with the order (unique constraint) and returns the existing order for the second request, so nothing is charged twice. The order ID is also passed to the payment service as its idempotency key.

**6. Payment fails after stock was reserved. What happens?**
Compensate: release the reserved units back to available, mark the order PAYMENT_FAILED, keep the cart so the user can retry with another card. The API returns 402.

**7. How do you handle a flash sale where 100k users want 1,000 units?**
Protect the inventory row: pre-load stock into Redis and use atomic `DECR` (reject when it goes negative); or split stock into many buckets to spread contention; or use a virtual waiting room/queue that admits buyers at a controlled rate. Also rate-limit and cache everything else (product page served from CDN).

**8. Why store the price on the order line?**
Prices change. The order must reflect what the customer saw and paid at checkout for receipts, refunds, and disputes. Never join back to the current catalog price.

**9. How do you design the cart?**
Key-value per user (Redis/DynamoDB): `cart:{userId} → {productId: qty}`. Anonymous carts are keyed by session and merged on login. Prices are re-read at checkout, since cached cart prices may be stale.

**10. How is search kept in sync with the product DB?**
Change data capture or product-updated events → indexer → Elasticsearch. Search is eventually consistent (seconds). Checkout always re-validates price and stock against the source of truth.

**11. How would you shard the data?**
Orders by user ID (users read their own orders). Inventory by product (or product + warehouse). Catalog is read-mostly and replicated widely. Avoid cross-shard transactions in checkout by keeping one order's inventory updates in one region or warehouse when possible.

**12. What happens if the checkout service crashes after charging but before saving the order?**
The saga log shows "charged, not confirmed". A recovery job either completes the order (stock is still reserved) or refunds the charge. Because payment calls are idempotent, recovery can safely retry them.

**Likely follow-ups:** recommendations ("customers also bought") · order cancellation and returns (refund + restock) · multiple warehouses (choose fulfilment location) · coupons and promotions (price calculation service).
