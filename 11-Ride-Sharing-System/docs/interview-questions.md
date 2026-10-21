# Ride Sharing System: Interview Questions

**1. Estimate location update throughput.**
Assume 5M online drivers, one ping every 4 s: **1.25M writes/s**. Each update is small (~100 bytes), so ~125 MB/s of ingress. This dominates the write load, which is why locations live in memory and aren't written to a durable DB per ping.

**2. How do you find nearby drivers efficiently?**
Bucket the map into cells (grid, geohash, or H3 hexagons) and keep `cell → drivers`. For a pickup, compute the cells covering the search radius, gather their drivers, then filter and sort by exact distance (haversine) or ETA. This costs O(drivers in a few cells) instead of O(all drivers).

**3. Geohash vs quadtree vs H3?**
Geohash: string prefixes encode cells, so nearby points share prefixes; it's simple and supported by Redis GEO, but cells are rectangles with edge distortions. Quadtree: adapts cell size to density, but is harder to distribute. H3 (Uber): hexagons with uniform neighbour distances and multiple resolutions. All need neighbouring-cell searches.

**4. Why search neighbouring cells?**
A driver just across a cell boundary can be closer than one inside the pickup's cell. Always include all cells overlapping the search circle.

**5. How do you prevent two riders from getting the same driver?**
An atomic compare-and-set on driver state: `AVAILABLE → ON_TRIP` succeeds for exactly one request. In Redis, a Lua script or `SET driver:42:lock rider NX EX 20`; in SQL, `UPDATE drivers SET status='ON_TRIP' WHERE id=42 AND status='AVAILABLE'` and check the affected row count.

**6. What happens when a driver declines or doesn't respond?**
Release the reservation (back to AVAILABLE) and offer to the next candidate. Use a timeout (~15 s) for no response. Don't re-offer the same trip to drivers who already declined, even if the search radius widens.

**7. Do you store every GPS ping in a database?**
No. The live index is in memory and overwritten. Pings are also published to Kafka and land in cheap storage (data lake) for trip replay, disputes, and analytics. Only trip records go to the transactional DB.

**8. How does the rider see the driver approaching?**
The driver's pings flow through Kafka to a trip-tracking service, which pushes positions to the rider's app over WebSocket (or the app polls every few seconds). ETA is recomputed with a routing engine.

**9. How does surge pricing work?**
Per cell (or area), compare demand (open requests) with supply (available drivers) over a short window, computed by a stream job on the location and request streams. A ratio above a threshold raises a multiplier (capped). It's shown to the rider upfront.

**10. Greedy nearest-driver vs batch matching?**
Greedy is simple and fast but globally suboptimal (it can take the only driver near rider B to serve rider A who had other options). Batch matching collects requests for 1–2 seconds and solves an assignment problem to minimize total ETA, which works better in dense cities.

**11. How do you shard the system?**
By geography (city or region). Matching is inherently local, so each shard holds its region's index, driver states, and dispatch logic. Cross-region trips are rare and handled at the boundary.

**12. What if the location service in one region goes down?**
Drivers' apps buffer and retry. The index goes stale, so matching should degrade (widen search, show longer ETAs) rather than assign based on very old positions; ignore locations older than N seconds. Other regions are unaffected.

**Likely follow-ups:** ride pooling (match several riders on compatible routes) · ETA computation (routing graph + live traffic) · fraud (GPS spoofing detection) · payments at trip end (see [14-Payment-System](../../14-Payment-System/README.md)).
