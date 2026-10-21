# E-commerce System: Design Summary

## Problem Statement
Customers browse millions of products, add them to carts, and buy them. Browsing must be fast and survive huge traffic spikes (sales events). Checkout must be correct: never sell stock that doesn't exist, never charge twice, and leave nothing half-done when payment fails.

## Functional Requirements
1. Browse and search the product catalog.
2. Add and remove items in a cart.
3. Check out: validate stock, charge payment, create the order.
4. View order status.

## Non-Functional Requirements
- **Read-heavy:** browse:buy ratio is ~100:1 or higher. Catalog reads must be cheap and cacheable.
- **Strong consistency for inventory and payment;** eventual consistency is fine for search and recommendations.
- **High availability** during flash sales (10–100× normal traffic).
- **Idempotency:** retries and double-clicks must not create duplicate orders or charges.
- **Scale (assumed):** 50M DAU, 1M orders/day, flash-sale peaks of ~5k checkouts/s.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Browse path:** catalog service + **Elasticsearch** for search, fronted by caches and a CDN for images. Stale by seconds is fine.
- **Cart service:** carts in **Redis**/DynamoDB. Adding to a cart doesn't reserve stock (abandoned carts would lock inventory).
- **Checkout service** runs a **saga** with an **idempotency key**:
  1. **Reserve stock** with a conditional decrement (all-or-nothing across items).
  2. **Charge** through the payment service (the order ID is the payment idempotency key).
  3. **Save the order** as PAID and commit stock, or on failure **compensate**: release stock, mark PAYMENT_FAILED, keep the cart.
- **Kafka `order-placed`** feeds fulfilment, emails, and analytics asynchronously.

## API Design
```http
GET  /api/products?q=keyboard                          → catalog (cacheable)
POST /api/carts/{userId}/items  {"productId":"p1","quantity":2}   → cart with total
POST /api/orders                Idempotency-Key: 7f3a
     {"userId":"alice"}
→ 201 Created          {"id":"ord-1","status":"PAID","totalCents":999800,...}
→ 402 Payment Required {"id":"ord-2","status":"PAYMENT_FAILED",...}   (stock released)
→ 409 Conflict         out of stock (nothing reserved, nothing charged)
GET  /api/orders/{id}
```

## Data Model
| Table | Key columns | Notes |
|---|---|---|
| `products` | `id`, `name`, `price`, `attributes` | Source of truth; indexed into Elasticsearch |
| `inventory` | `product_id`, `warehouse_id`, `available`, `reserved`, `version` | Small, hot rows; strongly consistent |
| `orders` | `id`, `user_id`, `status`, `total`, `idempotency_key` UNIQUE | |
| `order_lines` | `order_id`, `product_id`, `name`, `unit_price`, `quantity` | **Price snapshot** at checkout |
| `carts` (Redis) | `cart:{user}` → hash of `product → qty` | TTL e.g. 30 days |

**No-oversell update:**
```sql
UPDATE inventory SET available = available - :qty, reserved = reserved + :qty
 WHERE product_id = :id AND available >= :qty;   -- 0 rows → out of stock, roll back the transaction
```

## Key Design Decisions
| Decision | Why |
|---|---|
| **Separate read and write paths** | Catalog/search scales with caches and replicas; checkout needs transactions on a much smaller dataset. |
| **Reserve at checkout, not add-to-cart** | Abandoned carts would otherwise lock stock; contention only happens at the moment of purchase. |
| **Conditional decrement** | Atomic check-and-update in the DB; no read-then-write race between buyers. |
| **Saga with compensation** | Inventory and payment live in different services; there's no distributed transaction, so failures are undone explicitly. |
| **Idempotency key per checkout** | Network retries and double-clicks return the original order instead of buying twice. |
| **Price snapshot on order lines** | Later price changes never change what a customer agreed to pay. |

## Bottlenecks and Trade-offs
- **Hot products in flash sales:** thousands of buyers hit one inventory row. Options: split stock into N sub-buckets (`product_id, bucket`) and pick one randomly; pre-load stock into Redis with atomic `DECR`; or put buyers in a queue and process sequentially.
- **Payment latency** holds reservations. Use reservation timeouts so stuck checkouts release stock.
- **Saga failure in the middle** (charged but crashed before saving the order): record saga state, and on recovery either complete or refund. The payment service's idempotency makes retries safe.
- **Search freshness:** index updates lag the product DB by seconds; always re-check price and stock at checkout.

## Scaling Strategy
1. **Monolith + one PostgreSQL** with transactions across inventory and orders (simple and strongly consistent).
2. **Cache and CDN** the catalog; Elasticsearch for search; read replicas.
3. **Split services** (catalog, cart, checkout, inventory, payment) with a saga and Kafka events.
4. **Shard orders by user ID** and inventory by product/warehouse; hot-item techniques for flash sales.
5. **Multi-region:** browse served everywhere; inventory and checkout homed per region or warehouse.
