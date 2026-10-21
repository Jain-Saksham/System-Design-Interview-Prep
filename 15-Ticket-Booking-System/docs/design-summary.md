# Ticket Booking System: Design Summary

## Problem Statement
Customers pick specific seats for a show and pay. Unlike generic inventory, each seat is unique, and popular events see thousands of people competing for the same seats within seconds. The system must never sell a seat twice, must let users finish paying without losing their seats, and must not let abandoned checkouts lock seats forever.

## Functional Requirements
1. View the seat map with live availability.
2. Select seats and hold them for a few minutes.
3. Pay and confirm the booking within the hold window.
4. Release seats when a hold expires or the user cancels.

## Non-Functional Requirements
- **No double booking:** strong consistency for seat state.
- **Fairness and survivability** under on-sale spikes (100× normal traffic in minutes).
- **Low latency** for seat map reads (served from cache).
- **Availability** of browsing even when booking is overloaded.
- **Scale (assumed):** 10M bookings/month; a hot concert: 500k users for 50k seats in the first 10 minutes.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Seat map reads** come from a Redis cache built from the seats table. Slightly stale is fine; the hold step re-validates.
- **Virtual waiting room** (hot events only) queues users and admits them at a rate the booking service can handle.
- **Hold:** the booking service atomically marks all requested seats HELD with an expiry, all-or-nothing, using a conditional update.
- **Pay:** via the payment service, with the hold ID as the idempotency key.
- **Confirm:** seats move from HELD to BOOKED and a booking row is created. Confirmation is idempotent.
- **Expiry:** expired holds count as free immediately (checked at hold time), and a **sweeper** cleans them up. A booking-confirmed event drives e-tickets and email.

## API Design
```http
GET  /shows/dune-7pm/seats                              → seat map (cached)
POST /shows/dune-7pm/holds   {"seats":["A3","A4"]}      → 201 {"holdId":"hold-2","expiresAt":"..."} | 409 {"unavailable":["A3"]}
POST /holds/hold-2/confirm   (after payment)            → 201 {"bookingId":"book-1","seats":["A3","A4"]} | 410 Gone (hold expired)
DELETE /holds/hold-2                                    → 204 (seats released)
```

## Data Model
| Table | Key columns | Notes |
|---|---|---|
| `shows` | `id`, `venue_id`, `starts_at` | |
| `show_seats` | (`show_id`, `seat_id`) PK, `status` (AVAILABLE/HELD/BOOKED), `hold_id`, `hold_expires_at`, `booking_id`, `version` | One row per seat per show; the unit of locking |
| `holds` | `id`, `user_id`, `show_id`, `expires_at` | |
| `bookings` | `id`, `user_id`, `show_id`, `seats`, `amount`, `payment_id` | Permanent |

**Atomic multi-seat hold:**
```sql
UPDATE show_seats SET status='HELD', hold_id=:hold, hold_expires_at=now() + interval '5 min'
 WHERE show_id=:show AND seat_id IN (:seats)
   AND (status='AVAILABLE' OR (status='HELD' AND hold_expires_at < now()));
-- rows updated ≠ number of seats requested → ROLLBACK (someone else has at least one)
```

## Key Design Decisions
| Decision | Why |
|---|---|
| **Hold before payment** | Users need time to pay without someone else grabbing their seats mid-checkout. |
| **Short hold expiry (5–10 min)** | Abandoned checkouts release seats quickly. |
| **Lazy expiry + sweeper** | Correctness doesn't depend on the sweeper running on time; the sweeper only tidies up. |
| **All-or-nothing holds** | A group booking 4 seats together must not end up with 2. |
| **Conditional update (or `SELECT ... FOR UPDATE`)** | The database enforces "only one winner per seat"; no read-then-write races. |
| **Waiting room for hot events** | Admission control keeps the booking path within capacity and gives users a fair, honest queue instead of errors. |

## Bottlenecks and Trade-offs
- **Hot seats** (front row) cause most conflicts. Fail fast with a clear 409 and suggest nearby seats.
- **DB write load during on-sales:** shard by show; the waiting room caps the admission rate; consider Redis (`SET seat:A3 hold NX EX 300`) as a fast first gate with the DB as source of truth.
- **Paid but hold expired:** rare but possible (slow payment). Automatically refund, or extend the hold while payment is in progress.
- **Pessimistic vs optimistic locking:** `FOR UPDATE` serializes access per seat (simple, can queue); optimistic version checks retry on conflict (better under low contention).
- **Seat map staleness:** the cache may show a seat as free that was just held. The hold step is authoritative.

## Scaling Strategy
1. **Single DB with row locks** (works for a single cinema chain).
2. **Cache seat maps in Redis;** sweeper job; idempotent confirm with the payment service.
3. **Shard by show/venue:** contention is per show, so shards don't interact.
4. **Waiting room + bot protection** for large on-sales; Redis as a fast seat-lock layer.
5. **Multi-region:** home each show in one region (strong consistency), serve seat maps globally from caches.
