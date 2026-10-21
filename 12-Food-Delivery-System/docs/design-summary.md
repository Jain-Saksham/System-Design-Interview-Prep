# Food Delivery System: Design Summary

## Problem Statement
A customer orders from a restaurant; the restaurant cooks; a courier picks up and delivers. Three parties with three apps must agree on the order's state at every step, food should be hot on arrival, and the customer wants an accurate, live ETA.

## Functional Requirements
1. Customers place orders and track them live.
2. Restaurants accept or reject orders and mark them ready.
3. The system assigns a courier; couriers mark pickup and delivery.
4. Customers can cancel only before cooking starts.
5. Show an estimated delivery time.

## Non-Functional Requirements
- **Correctness of order state:** no impossible transitions (delivered before picked up), no lost orders.
- **Timeliness:** dispatch within seconds; status updates reach the customer in near real time.
- **Availability** during meal-time peaks (lunch/dinner are ~5× average load).
- **Scale (assumed):** 2M orders/day, peaking at ~150 orders/s; 200k active couriers sending locations.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Order service** owns the order and its **state machine**. Every action from any app is a transition validated against the allowed next states and persisted with a status history.
- Each transition publishes an event to **Kafka `order-events`**.
- **Dispatch service** consumes `ACCEPTED` events, finds nearby free couriers in the **courier location index** (Redis GEO), reserves one atomically, and sends a job offer to the courier app.
- **Tracking/notification service** consumes events and pushes status and ETA updates to the customer (push notifications, WebSocket).
- Courier apps stream GPS pings to keep the location index and ETAs fresh.

## API Design
```http
POST /orders                       { "restaurantId": "r1", "items": [...], "deliveryAddress": {...} }  → 201 { "orderId": "o1001", "status": "PLACED" }
POST /orders/o1001/accept          (restaurant)    → 200 { "status": "ACCEPTED" }
POST /orders/o1001/preparing | /ready              (restaurant)
POST /orders/o1001/pickup | /deliver               (courier)
POST /orders/o1001/cancel          (customer)      → 200, or 409 Conflict if cooking has started
GET  /orders/o1001                 → status, history, courier, ETA
```
`409 Conflict` signals an invalid transition, so clients don't need to know the state machine.

## Data Model
| Table | Key columns |
|---|---|
| `orders` | `id`, `customer_id`, `restaurant_id`, `courier_id`, `status`, `total`, `delivery_location`, `version` |
| `order_status_history` | `order_id`, `status`, `changed_at`, `actor` (audit + analytics) |
| `restaurants` | `id`, `location`, `avg_prep_minutes`, `is_open` |
| `couriers` | `id`, `status` (FREE/BUSY/OFFLINE), `vehicle` |
| Courier locations (Redis GEO) | `courier_id → lat/lng`, refreshed every few seconds |

Orders need transactions and strong consistency, so PostgreSQL, sharded by `order_id` or city at scale. Status updates use **optimistic locking** (`UPDATE ... WHERE id=? AND status=? AND version=?`) so concurrent actions can't corrupt state.

## Key Design Decisions
| Decision | Why |
|---|---|
| **Explicit state machine** | One place defines legal transitions; every app's action is validated; bugs can't create impossible states. |
| **Event-driven side effects** | Dispatch, notifications, and analytics react to events instead of being called inline, so the order service stays simple and resilient. |
| **Dispatch timing** | Assign so the courier arrives when food is ready. Too early wastes courier time; too late leaves food waiting. |
| **ETA = max(prep, courier travel) + delivery** | Captures both waiting cases; refined continuously with live locations. |
| **Atomic courier reservation** | Prevents two orders from grabbing the same courier during peaks. |
| **Cancellation window** | Before cooking only, which protects restaurants from wasted food. |

## Bottlenecks and Trade-offs
- **Meal-time peaks:** autoscale stateless services; Kafka absorbs bursts; dispatch may batch orders every few seconds.
- **Courier shortage:** orders wait for dispatch (retry loop), the customer sees a longer ETA, and surge incentives attract couriers.
- **Batching orders per courier** improves efficiency but delays the first order; cap the extra delay.
- **Event ordering:** consumers must handle duplicates and out-of-order events. Include status and version in events and ignore stale ones.
- **Prep time accuracy** drives ETA quality; ML models per restaurant, item, and time of day.

## Scaling Strategy
1. **Single service + PostgreSQL;** dispatch is a synchronous call on acceptance (this repo's shape).
2. **Event-driven:** Kafka between order service, dispatch, and notifications; Redis GEO for couriers.
3. **Shard by city:** orders, dispatch, and courier indexes per city (deliveries are local).
4. **Smarter dispatch:** batching, delayed assignment, ML prep and ETA predictions.
5. **Multi-region active-active,** with each city homed in one region.
