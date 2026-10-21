# Ride Sharing System: Design Summary

## Problem Statement
Riders want a car within minutes; drivers want steady trips. The platform must know where every available driver is, right now, find the best ones near a pickup in milliseconds, offer the trip, and track it to completion, for millions of concurrent users across many cities.

## Functional Requirements
1. Drivers go online/offline and stream their location.
2. Riders request a ride (pickup, drop-off) and see an upfront fare.
3. Match the rider with a nearby available driver; the driver can accept or decline.
4. Track trip state: assigned → in progress → completed.

## Non-Functional Requirements
- **Low-latency matching:** a driver found within a few seconds.
- **High write throughput** for location updates (the dominant load).
- **Consistency for assignments:** a driver is never assigned to two riders at once.
- **Availability:** matching must keep working in each city even if others have problems.
- **Scale (assumed):** 5M active drivers sending a ping every 4 s; 1M trip requests/hour at peak.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).

**Location path (high volume):** driver apps send GPS pings to the **location service** over persistent connections. It updates the driver's cell in the **geo index** (in-memory, Redis GEO or H3) and publishes to **Kafka** for ETAs, trip tracking, and surge analytics.

**Trip path:** the rider's request goes to the **ride service**, which asks the **matching service** for candidates. Matching queries the geo index for drivers near the pickup (widening the radius if needed), filters to available ones, ranks by distance or ETA, and **atomically reserves** the best driver. The offer is pushed through the **driver gateway**. On accept, the trip is persisted; on decline or timeout, the driver is released and the next candidate gets the offer.

## API Design
```http
POST /drivers/me/location      { "lat": 12.9755, "lng": 77.6065, "heading": 90 }   (or WebSocket frames)
POST /rides                    { "pickup": {...}, "dropoff": {...} }
→ 201 { "tripId": "t2", "driverId": "asha", "fareEstimate": 108.30, "status": "DRIVER_ASSIGNED" }
POST /offers/{id}/accept | /decline                                               (driver app)
POST /trips/{id}/start | /complete
GET  /trips/{id}               → status, driver location (rider tracking)
```

## Data Model
| Data | Store | Notes |
|---|---|---|
| Driver location | Geo index in memory (Redis GEO / H3 cell → drivers) | Ephemeral, overwritten every few seconds; no need for durability |
| Driver state | Redis / KV: `driver_id → AVAILABLE \| ON_TRIP \| OFFLINE` | Changed with atomic compare-and-set |
| Trips | PostgreSQL: `trip_id`, `rider_id`, `driver_id`, pickup, dropoff, status, fare, timestamps | Durable, transactional, audited |
| Location history | Kafka → data lake | Trip replay, analytics, fraud detection |

## Key Design Decisions
| Decision | Why |
|---|---|
| **Spatial bucketing (grid / geohash / H3)** | "Nearby" becomes a lookup of a few cells instead of a distance check against every driver. |
| **Search neighbouring cells** | A driver 50 m away can sit in the next cell; searching only the pickup's cell would miss them. |
| **In-memory location index** | Locations change every few seconds and are useless after a minute; durability isn't needed, speed is. |
| **Atomic reservation (CAS)** | Two concurrent requests can both see a driver as available; only one CAS from AVAILABLE→ON_TRIP succeeds. |
| **Shard by city/region** | Matching is local, so each region scales and fails independently. |
| **Kafka for the location stream** | Many consumers (ETA, tracking, surge, analytics) without slowing ingestion. |

## Bottlenecks and Trade-offs
- **Location write volume:** 5M drivers / 4 s ≈ **1.25M updates/s**. Shard the index by region, batch updates, and only send updates when the driver has moved meaningfully.
- **Hot cells** (airport, stadium): a single cell with thousands of drivers. Use finer cells (H3 resolution) in dense areas.
- **Greedy vs batched matching:** nearest-first per request is simple; batching requests for 1–2 s and solving an assignment problem gives better global ETAs.
- **Stale locations:** the index lags reality by a few seconds. Use ETA, not straight-line distance, and accept small errors.
- **Driver declines and timeouts** add latency; offer to several candidates in parallel with a hold, or predict acceptance likelihood.

## Scaling Strategy
1. **Single service + SQL with lat/lng columns** and a bounding-box query (fine for one small city).
2. **In-memory geo index** (Redis GEO) and a separate location service; Kafka for the location stream.
3. **Shard by city/region:** each region has its own index, matching, and dispatch.
4. **ETA-based matching** with a routing engine; batched matching; surge pricing from supply/demand per cell.
5. **Multi-region active-active;** trips in a globally replicated DB keyed by region.
