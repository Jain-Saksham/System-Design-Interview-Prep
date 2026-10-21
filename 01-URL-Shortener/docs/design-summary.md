# URL Shortener: Design Summary

## Problem Statement
Long URLs are awkward to share, print, or fit in a message. A URL shortener maps a short, unique code to a long URL and redirects anyone who visits the code. The system is **extremely read-heavy**: a link is created once and clicked many times.

## Functional Requirements
1. Create a short link for a long URL.
2. Redirect `GET /{code}` to the original URL.
3. Optional custom alias (`/my-talk`).
4. Optional expiry; expired links stop working.
5. Basic analytics: click count per link.

## Non-Functional Requirements
- **Low-latency redirects:** p99 in the tens of milliseconds; this is the hot path.
- **High availability for reads:** a broken redirect breaks every place the link was shared.
- **Uniqueness:** two long URLs must never share a code.
- **Durability:** once created, a link must not be lost.
- **Scale (assumed):** 100M new links/month, 100:1 read:write ratio, 5-year retention.
- **Security:** reject non-http(s) URLs (e.g., `javascript:`), rate-limit creation, screen for malicious URLs.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Load balancer** spreads traffic over stateless URL service instances.
- **Shorten API** validates input, gets a unique ID, Base62-encodes it, and inserts the mapping.
- **ID Range Allocator:** each instance leases a block of IDs from the database (e.g., 10,000 at a time), so IDs are unique across instances without a network call per request.
- **Redirect API** checks **Redis** first, falls back to **PostgreSQL** on a miss, then fills the cache.
- **Click events** are published to **Kafka** asynchronously; an aggregator writes batched counts to an analytics store. Redirect latency never waits for analytics.

## API Design
```http
POST /api/urls
{ "longUrl": "https://example.com/very/long/path", "customAlias": "talk", "expiresInDays": 30 }

201 Created
Location: https://sho.rt/talk
{ "code": "talk", "shortUrl": "https://sho.rt/talk", "longUrl": "https://example.com/very/long/path",
  "createdAt": "2026-01-01T00:00:00Z", "expiresAt": "2026-01-31T00:00:00Z", "clicks": 0 }
```
```http
GET /talk             → 302 Found, Location: https://example.com/very/long/path
GET /api/urls/talk    → 200 OK with details and click count
```
Errors: `400` invalid URL/alias · `404` unknown code · `409` alias taken · `410` expired.

## Data Model
**`short_urls`** (PostgreSQL)

| Column | Type | Notes |
|---|---|---|
| `code` | `VARCHAR(30)` **PK** | Generated Base62 code or custom alias |
| `long_url` | `TEXT` | Max 2048 chars enforced by the app |
| `created_at` | `TIMESTAMPTZ` | |
| `expires_at` | `TIMESTAMPTZ` NULL | Index it for the cleanup job |

- The access pattern is a single key lookup, so any store with fast primary-key reads works. PostgreSQL is chosen for simplicity and strong uniqueness guarantees. A key-value store (DynamoDB, Cassandra) is a valid alternative at very large scale.
- Click counts live in a separate analytics store, so heavy write traffic stays out of the URL table.

## Key Design Decisions
| Decision | Why |
|---|---|
| **Counter + Base62** instead of hashing | Guaranteed unique, no collision checks, short codes. Hashing (MD5 → first 7 chars) needs collision handling. |
| **ID block leasing** | Unique IDs across instances with one DB call per 10,000 links. |
| **302 instead of 301** | 301 is cached by browsers, so later clicks never reach us (no analytics, can't expire). Use 301 only if analytics don't matter and you want to minimize load. |
| **Atomic `saveIfAbsent`** | Two users racing for the same alias can't both succeed (DB unique constraint in production). |
| **Async click tracking** | Analytics writes would otherwise add latency and DB load to every redirect. |
| **Redis cache** | The read:write ratio is ~100:1 and popularity is skewed, so a small cache absorbs most reads. |

## Bottlenecks and Trade-offs
- **Hot links** (a viral link): served from Redis; add an in-process cache or CDN edge redirect for extreme cases.
- **Cache miss storms** after a Redis restart: use request coalescing (one DB read per key) and warm the cache.
- **Sequential codes are guessable:** anyone can enumerate links. Fix by scrambling IDs (e.g., a reversible bit permutation) or using random codes with a uniqueness check.
- **Lost ID blocks:** if an instance crashes, its unused IDs are skipped. That's harmless, since there's plenty of key space.
- **Analytics lag:** counts are eventually consistent (seconds behind), which is acceptable for this feature.

## Scaling Strategy
1. **Single instance + PostgreSQL** (this repo's shape, with a real DB). Handles thousands of redirects/sec.
2. **Add Redis** for reads, and **multiple stateless instances** with ID block leasing.
3. **Read replicas** for cache misses; move click tracking to **Kafka**.
4. **Shard** `short_urls` by `code` hash when one primary can't hold the data or write load. Code lookups stay single-shard.
5. **Multi-region:** replicate the read path globally (cache + replicas, or edge redirects); keep writes in one region or partition the ID space per region.
