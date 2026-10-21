# Distributed Cache: Interview Questions

**1. Estimate the cluster size.**
Assume a hot set of 500 GB and nodes with 64 GB RAM, using ~50 GB for data after overhead: 500 / 50 = **10 nodes**, or ~20 with a replica each. Throughput: if one node handles ~100k ops/s, 10 nodes give ~1M ops/s. Check whichever limit (memory or ops) binds first.

**2. How do you implement an LRU cache with O(1) operations?**
A hash map from key to a node in a doubly linked list. `get`: look up the node, move it to the front. `put`: insert at the front; if over capacity, remove the node before the tail sentinel and delete it from the map. Sentinel head and tail nodes avoid null checks.

**3. LRU vs LFU vs TTL-only?**
LRU evicts what hasn't been used recently; it's simple and good for recency-driven workloads. LFU evicts the least frequently used; it's better when popularity is stable, but needs counters and aging. TTL bounds staleness and is used alongside an eviction policy. Redis offers approximated LRU and LFU.

**4. Why consistent hashing instead of `hash(key) % N`?**
With modulo, changing N remaps almost every key (~75% when going from 3 to 4 nodes), causing a miss storm that can overwhelm the database. Consistent hashing moves only ~1/N of keys, and only to or from the changed node.

**5. What are virtual nodes, and why use them?**
Each physical node is placed on the ring many times (100–200). This evens out key distribution, lets you weight bigger machines with more virtual nodes, and spreads a failed node's keys across all remaining nodes instead of dumping them on one neighbour.

**6. How do you keep the cache consistent with the database?**
Cache-aside with "update the DB, then delete the cache key". The next read reloads fresh data. Deleting is safer than updating the cache (concurrent updates can write stale values). TTLs bound any remaining inconsistency. For stricter needs, invalidate via change-data-capture events.

**7. What is a cache stampede and how do you prevent it?**
When a hot key expires, many concurrent requests miss and all hit the DB. Prevent it with request coalescing (a per-key lock or singleflight, so one request loads and others wait), probabilistic early refresh, or TTL jitter so keys don't expire together.

**8. How do you handle a hot key?**
Replicate it across several nodes (`key#1..key#k`, pick one randomly on read), cache it in-process on app servers (an L1 cache with a short TTL), or split the value. Detect hot keys via sampling and metrics.

**9. What happens when a cache node dies?**
Without replication, its keys become misses; consistent hashing sends them to the next nodes, which fill from the DB. Plan DB headroom for that. With replication, clients read the replica and the cluster promotes it. Health checks remove the dead node from the ring.

**10. Write-through vs write-back vs cache-aside?**
Cache-aside: the app manages the cache, which is resilient but has a first-read miss. Write-through: write to the cache and DB together, so the cache is always warm but writes are slower. Write-back: write to the cache and flush to the DB later, which is fast but risks data loss on crash. Cache-aside is the default for most web apps.

**11. Should the cache be in-process or remote?**
In-process (Caffeine) is fastest (no network) but duplicated on every server and inconsistent across them, so it suits small, rarely changing data. Remote (Redis) is shared and large, with a network hop. Many systems use both: a short-TTL L1 in-process, plus a remote L2.

**12. How is expiry implemented without a timer per key?**
Lazy expiry: check the deadline on access and delete if expired. Plus active expiry: periodically sample random keys with TTLs and delete the expired ones (Redis samples 20 keys, repeating if many were expired). Memory is reclaimed without per-key timers.

**Likely follow-ups:** Redis Cluster's 16,384 hash slots vs a ring · cache warming after deploys · serialization formats and value sizes · using the cache for sessions and rate limiting (see [02-Rate-Limiter](../../02-Rate-Limiter/README.md)).
