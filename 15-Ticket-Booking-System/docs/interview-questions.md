# Ticket Booking System: Interview Questions

**1. Estimate the load during a hot on-sale.**
Assume 500k users for 50k seats in 10 minutes. If each user makes ~20 requests (seat maps, attempts): 10M requests in 600 s ≈ **17k req/s**, mostly reads. Hold attempts may be ~2–5k/s, concentrated on one show, so this is a contention problem, not a storage problem.

**2. How do you guarantee a seat is never sold twice?**
Make the state change atomic in the database: a conditional update that only succeeds if the seat is AVAILABLE (or its hold has expired), or `SELECT ... FOR UPDATE` then update in one transaction. The database picks exactly one winner. Never check availability in application code and then write separately.

**3. Why hold seats before payment instead of booking directly?**
Payment takes time (and can involve 3-D Secure redirects). Without a hold, another user could buy the seat mid-payment. The hold reserves it temporarily; confirmation after payment makes it permanent.

**4. How do holds expire?**
Store `hold_expires_at`. Treat expired holds as free whenever availability is checked (lazy expiry), so correctness never depends on a background job. A sweeper periodically resets expired holds for cleanliness. With Redis, a key TTL does the same.

**5. A user selects 4 seats but one is taken. What happens?**
All-or-nothing: the transaction holds none of them and returns 409 with the unavailable seat, so the user can choose again. Partial holds would leave groups split up.

**6. What if payment succeeds but the hold expired just before confirmation?**
Confirmation fails (410 Gone) if the seats were taken by someone else. The payment must be refunded automatically. To reduce this: extend the hold while a payment is in progress, and make the hold slightly longer than the payment UI timeout.

**7. How do you make confirmation safe to retry?**
Key it by hold ID: if a booking already exists for that hold, return it. Pass the hold ID as the payment's idempotency key, so retries never charge twice either.

**8. Pessimistic or optimistic locking?**
Pessimistic (`FOR UPDATE`) is simple and fair under heavy contention, but holds locks during the transaction. Optimistic (version column) avoids locks but causes retries when many users hit the same seat. For hot seats with very short transactions, either works; keep transactions tiny.

**9. How do you handle 500k users arriving at once?**
A virtual waiting room in front of the booking flow: users get a queue position and are admitted at a rate the system can handle. Serve seat maps from cache and CDN. Rate-limit per user and detect bots. Shed load early rather than letting databases melt.

**10. How should the seat map stay up to date?**
Cache it in Redis, updated via change events from the seats table (or invalidated on each hold or confirm). It can be a second stale; the hold step re-validates. For live updates, push changes to clients viewing that show over WebSocket or SSE.

**11. How do you shard the data?**
By show (or venue). All seats of one show live together, so a multi-seat hold is a single-shard transaction. Shows never interact, so shards scale independently.

**12. Could you use Redis instead of the database for seat locks?**
As a fast first gate: `SET lock:show:seat holdId NX EX 300` per seat (a Lua script for all-or-nothing across seats). The database remains the source of truth for confirmed bookings, and a confirm must re-check there. Redis alone risks losing holds on failover.

**Likely follow-ups:** dynamic pricing as seats sell · seat recommendations ("best available") · resale and transfers · preventing scalpers (purchase limits, identity checks).
