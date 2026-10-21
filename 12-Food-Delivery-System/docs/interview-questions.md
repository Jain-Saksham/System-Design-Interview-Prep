# Food Delivery System: Interview Questions

**1. Estimate peak order rate.**
Assume 2M orders/day. If ~60% fall in a 4-hour lunch-and-dinner window: 1.2M / 14,400 s ≈ **85 orders/s**, peaking at maybe 150–200/s. Each order generates ~7 status events plus courier location pings, so events and locations dominate traffic, not order creation.

**2. Why model the order as a state machine?**
Three different apps act on the same order. A state machine with explicit allowed transitions rejects impossible actions (delivering an unpicked order, cancelling after cooking) in one place, and makes the lifecycle easy to reason about, test, and audit.

**3. How do you prevent two conflicting updates to the same order?**
Optimistic locking: `UPDATE orders SET status='PICKED_UP', version=version+1 WHERE id=? AND status='READY_FOR_PICKUP' AND version=?`. If zero rows change, someone else moved first; reload and re-validate. The in-memory version uses `synchronized` on the order.

**4. When should a courier be dispatched?**
Not necessarily on acceptance. Ideally, the courier arrives at the restaurant just as food is ready: dispatch at roughly `ready_time − courier_travel_time`. Too early wastes courier capacity; too late means cold food.

**5. How do you estimate delivery time?**
`max(remaining prep time, courier travel to restaurant) + travel to customer` + pickup buffer. Travel comes from a routing engine with live traffic; prep time from ML models per restaurant and item. Update the ETA as events and GPS pings arrive.

**6. How do status updates reach the customer in real time?**
Order events go to Kafka; a notification service consumes them and pushes to the customer via WebSocket (app open) or push notification (app closed). Courier location for the map comes from the location stream, throttled to a few updates per minute.

**7. What happens if no courier is available?**
The order stays ACCEPTED (or PREPARING) without a courier. A dispatch loop retries every few seconds, widening the search radius. The customer sees a longer ETA. Operationally, surge pay attracts more couriers.

**8. How do you batch multiple orders for one courier?**
When two orders share a restaurant (or nearby restaurants) and directions, assign both if the extra delay to the first order stays under a threshold (e.g., 5 minutes). Batching is an optimization over a short time window, similar to batched ride matching.

**9. How do you handle the restaurant tablet going offline?**
Orders aren't auto-accepted. If there's no response in N minutes, escalate (phone call or ops alert) and then auto-cancel with a refund. Mark the restaurant temporarily closed so new orders aren't accepted.

**10. Why publish events instead of calling dispatch and notification services directly?**
Decoupling and resilience. If notifications are slow or down, the order flow continues and events are processed when the service recovers. New consumers (analytics, fraud) subscribe without changing the order service.

**11. How do you shard the system?**
By city or region. Orders, couriers, and restaurants interact locally, so each city's dispatch and courier index can live together. Order IDs can embed the region for routing.

**Likely follow-ups:** payments and refunds on cancellation (see [14-Payment-System](../../14-Payment-System/README.md)) · restaurant search and menus (search index + cache) · courier earnings and incentives · handling a courier who abandons an order mid-way.
