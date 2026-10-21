# Search Autocomplete: Design Summary

## Problem Statement
As users type in a search box, show the top few most likely completions. It saves typing, steers users toward well-formed queries, and fires a request on nearly every keystroke, so it must be extremely fast and cheap per request.

## Functional Requirements
1. Return the top-k (e.g., 5–10) completions for a prefix.
2. Rank by popularity (search frequency).
3. Reflect new popular searches within hours.
4. Never suggest blocked (offensive, legally removed) queries.

## Non-Functional Requirements
- **Latency:** < 100 ms end to end; the server lookup should be single-digit milliseconds.
- **High availability:** autocomplete down = degraded search box (the search still works).
- **Scale (assumed):** 10M DAU × 10 searches/day × ~6 keystroke requests per search ≈ 600M requests/day ≈ **7k QPS** average.
- **Freshness:** hourly is fine; real-time trending is a separate, smaller system.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).

**Read path:** the browser debounces keystrokes and caches results. Popular short prefixes are served from the **CDN**. Misses go to **suggestion servers**, which hold the whole trie in memory and answer in O(prefix length).

**Build path (offline):** submitted searches are logged to **Kafka**. An **aggregation job** counts queries per time window (with decay so recent searches weigh more). The **trie builder** filters blocked terms, computes top-k per node, and publishes a **snapshot** to object storage. Suggestion servers load the new snapshot and swap it in atomically.

## API Design
```http
GET /suggest?q=jav&limit=5
200 { "prefix": "jav", "suggestions": ["javascript", "java", "java interview questions"] }
Cache-Control: public, max-age=3600
```
Cacheable by browser and CDN because results only change on rebuild.

## Data Model
| Data | Store | Notes |
|---|---|---|
| Search events | Kafka → data lake | `query`, `timestamp`, `locale`, `user_id` (optional) |
| Aggregated counts | Table: `query → weighted_count` per window | Output of the aggregation job |
| Trie snapshot | Object storage (serialized trie) | Versioned; servers load at startup and on new versions |
| Blocked terms | Small table/file | Applied at build time (and checked at serve time for urgent removals) |

**Trie node:** `children: char → node`, `topK: [(query, count)]`. Memory: storing top-k at every node multiplies storage by about k, so prune rare queries and cap query length.

## Key Design Decisions
| Decision | Why |
|---|---|
| **Precompute top-k per node** | A lookup becomes "walk the prefix, return a list" with no subtree traversal at request time. |
| **Build offline, serve read-only** | Writes (counting) and reads (serving) scale independently; the serving trie needs no locks. |
| **Atomic snapshot swap** | Readers never see a half-built trie; rollback is "load the previous version". |
| **Cache aggressively** | Short prefixes ("a", "th") are requested constantly and identical for everyone, so CDN hit rates are high. |
| **Debounce on the client** | Typing "javascript" fast shouldn't send 10 requests; ~100–200 ms debounce cuts traffic a lot. |
| **Filter at build time** | Blocked terms never enter the trie, so serving stays simple and fast. |

## Bottlenecks and Trade-offs
- **Memory:** top-k at every node is fast but heavy. Prune low-count queries, limit query length, or store top-k only at nodes up to depth N and compute deeper ones on demand.
- **Freshness vs cost:** hourly rebuilds miss breaking trends. Add a small real-time "trending" service merged at serve time.
- **Hot prefixes:** single letters dominate traffic; the CDN and browser caches absorb them.
- **Personalization** breaks shared caching (results differ per user). Personalize only a re-ranking layer on top of global suggestions.

## Scaling Strategy
1. **Single server with an in-memory trie** built from a counts file (this repo's shape).
2. **Replicate** the server behind a load balancer; every replica loads the same snapshot.
3. **Offline pipeline:** Kafka → aggregation → builder → snapshot store; hourly hot swaps.
4. **CDN + browser caching** for popular prefixes; most traffic never hits the servers.
5. **Shard by prefix** if the trie no longer fits in one machine's memory (e.g., a–m, n–z, with care for skew); per-locale tries.
