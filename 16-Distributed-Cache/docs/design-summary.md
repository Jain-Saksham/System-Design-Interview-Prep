# Distributed Cache: Design Summary

## Problem Statement
Databases are slow and expensive relative to memory. A distributed cache keeps frequently read data in RAM across many machines, so most reads return in under a millisecond and the database only sees misses. One machine's RAM isn't enough for the hot set, so data must be partitioned, and losing a node must not cause a stampede to the database.

## Functional Requirements
1. `get(key)`, `put(key, value, ttl)`, `delete(key)`.
2. Automatic eviction when memory is full (LRU).
3. Per-key expiry (TTL).
4. Add or remove nodes without remapping most keys.

## Non-Functional Requirements
- **Latency:** sub-millisecond on the server; ~1 ms round trip.
- **Throughput:** 100k+ ops/s per node; millions cluster-wide.
- **Scalability:** horizontal by adding nodes.
- **Availability:** tolerate node failures (replicas, or graceful degradation to the DB).
- **Consistency:** eventual, by design. A cache is a copy; staleness is bounded by TTL and invalidation.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Cache-aside:** the app reads the cache first; on a miss it reads the DB and writes the value back with a TTL.
- **Client-side consistent hashing:** each app server's cache client hashes the key onto a ring of virtual nodes to find the owner node (and replica nodes), with no central router.
- **Cache nodes** hold an LRU structure with TTLs in RAM. Expired entries are dropped lazily on access and by a background sampler.
- **Cluster config** (etcd/ZooKeeper) publishes ring membership; clients update their rings when nodes join or leave.
- **Replication** (optional): writes go to the owner and the next N−1 nodes on the ring; reads fall back to replicas.

## API Design
```text
GET key                       → value | (nil)
SET key value EX 300          → OK            (TTL 300 s)
DEL key                       → 1 | 0
```
Client library API: `Optional<V> get(K)`, `put(K, V, ttl)`, `delete(K)`; batch `getMany(keys)` to cut round trips.

## Data Model
Per node: `HashMap<key, Node>` + doubly linked list ordered by recency.

| Field | Purpose |
|---|---|
| `key`, `value` | Data (values are opaque bytes, often serialized JSON or protobuf) |
| `expiresAt` | TTL deadline; checked on access |
| `prev`, `next` | Recency order for O(1) LRU moves and eviction |

Ring: sorted map `hash(nodeId#i) → nodeId` with 100–200 virtual nodes per server.

## Key Design Decisions
| Decision | Why |
|---|---|
| **HashMap + doubly linked list** | O(1) lookup, O(1) move-to-front, O(1) evict-from-back. |
| **Lazy + sampled expiry** | No timer per key; expired keys cost nothing until touched or sampled (Redis does both). |
| **Consistent hashing** | Adding the 4th node moves ~25% of keys instead of ~75% with `hash % N`, avoiding a cluster-wide miss storm. |
| **Virtual nodes** | Evens out load and spreads a failed node's keys across all survivors instead of one neighbour. |
| **Cache-aside** | Simple and resilient: if the cache is down, the app still works (slower). |
| **TTLs on everything** | Bounds staleness even if an invalidation is missed. |

## Bottlenecks and Trade-offs
- **Hot keys** (one celebrity profile): a single node gets hammered. Replicate hot keys to several nodes with random suffixes, or add a small in-process L1 cache.
- **Cache stampede / thundering herd:** a popular key expires and thousands of requests hit the DB at once. Use request coalescing (one loader per key), early probabilistic refresh, or staggered TTLs (jitter).
- **Consistency on writes:** update DB then delete the cache key (not update it) to avoid races; a brief stale read window remains.
- **Node failure:** without replicas, its keys become misses and the DB absorbs a spike. Replication costs memory.
- **Memory fragmentation and large values:** cap value size; store big blobs elsewhere.

## Scaling Strategy
1. **Single cache node** next to the app (in-process or one Redis).
2. **Several nodes with client-side consistent hashing;** TTLs and cache-aside everywhere.
3. **Replication per shard** with automatic failover (Redis primary/replica + Sentinel, or Redis Cluster).
4. **Hot-key handling,** L1 in-process caches, request coalescing to prevent stampedes.
5. **Multi-region:** a separate cache per region (never cross-region reads on the hot path); invalidate via events.
