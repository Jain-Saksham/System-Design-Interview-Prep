# Rate Limiter: Design Summary

## Problem Statement
Without limits, one client (malicious, buggy, or just popular) can exhaust a service's capacity and degrade it for everyone. A rate limiter caps requests per client per time window and rejects the excess early and cheaply, before it reaches expensive backend work.

## Functional Requirements
1. Limit requests per client (API key, user ID, or IP) per time window.
2. Support different rules per client tier or endpoint (e.g., free: 100/min, paid: 1,000/min).
3. Reject excess requests with `429` and tell the client when to retry.
4. Work correctly across many gateway instances (a global limit, not per-instance).

## Non-Functional Requirements
- **Low latency:** adds ≤ 1–2 ms per request; it sits on every request's path.
- **High availability:** a limiter outage must not take down the API (decide fail-open vs fail-closed).
- **Accuracy:** small overshoot under extreme concurrency is acceptable; large overshoot isn't.
- **Scale (assumed):** 50k requests/sec across the gateway tier, ~1M distinct active clients.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Rate-limit middleware** in each API gateway instance identifies the client and picks the rule.
- **Rules** come from a config store and are cached locally (refreshed periodically), so there's no extra hop per request.
- **Redis** holds per-client counters shared by all gateway instances. One Lua script checks **and** updates the counter atomically in a single round trip.
- **Allowed** requests are forwarded to backend services. **Rejected** ones get `429` + `Retry-After` immediately.
- **Monitoring** tracks rejection rates to spot abuse or mis-sized limits (asynchronous, off the request path).

## Algorithms Compared
| Algorithm | How it works | Pros | Cons |
|---|---|---|---|
| **Token bucket** ✅ implemented | Bucket refills at rate *r*, holds ≤ *b* tokens; request spends 1 | Allows bursts, smooth average, O(1) memory | Two parameters to tune |
| Leaky bucket | Requests queue and drain at a fixed rate | Perfectly smooth output | Bursts wait or drop; adds latency |
| Fixed window counter | Count per calendar window (e.g., per minute) | Simplest, O(1) | **2× burst at window boundaries** |
| Sliding window log | Store every request timestamp, count those in the window | Exact | O(requests) memory per client |
| **Sliding window counter** ✅ implemented | Weighted previous window + current window | Near-exact, O(1) memory, no boundary burst | Approximation assumes even spread in previous window |

**Default interview choice:** token bucket for APIs (bursts are normal) and sliding window counter when you need a strict "N per window" guarantee.

## API Design
The limiter is middleware, not a public API. Its contract:
```java
boolean tryAcquire(String clientId);   // true → forward, false → 429
```
HTTP response when rejected:
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 12
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
```

## Data Model
Redis hash per client, with a TTL so idle clients disappear:

| Key | Fields | TTL |
|---|---|---|
| `rl:{rule}:{clientId}` | `tokens`, `last_refill_ms` | time to refill a full bucket |

Rules (`tier`, `endpoint`, `capacity`, `refill_per_sec`) live in a small config table or file. They change rarely and are cached in every gateway instance.

**Production token bucket in Redis** (reference only; this repo doesn't run Redis):
```lua
-- KEYS[1] = bucket key; ARGV = capacity, refill tokens per ms, now in ms
local capacity, rate, now = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill_ms')
local tokens = tonumber(bucket[1]) or capacity
local last   = tonumber(bucket[2]) or now
tokens = math.min(capacity, tokens + math.max(0, now - last) * rate)
local allowed = tokens >= 1
if allowed then tokens = tokens - 1 end
redis.call('HSET', KEYS[1], 'tokens', tokens, 'last_refill_ms', now)
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / rate))
return allowed and 1 or 0
```
Redis runs a script atomically, so concurrent gateways can't both spend the last token. That's the distributed equivalent of the `synchronized` block in `TokenBucketRateLimiter`.

## Key Design Decisions
| Decision | Why |
|---|---|
| **Limit at the gateway** | Rejects excess before it costs anything downstream; one place to enforce policy. |
| **Redis for shared counters** | In-memory speed, atomic scripts, TTLs. Per-instance counters would let each instance allow the full limit. |
| **Lua script, not GET + SET** | Read-modify-write from the app is a race: two gateways read "1 token left" and both allow. |
| **Lazy refill** | Compute tokens from elapsed time on each request; no background timers per client. |
| **Fail open on Redis outage** (usually) | Availability of the API beats perfect limiting. Fail closed for security-critical limits like login attempts. |

## Bottlenecks and Trade-offs
- **Redis round trip on every request:** keep Redis close to the gateway (same AZ), pipeline where possible, or use a **local pre-check**: each instance gets a share of the limit and syncs to Redis periodically (less accurate, much faster).
- **Hot client key:** one huge client hammers one Redis shard. Use a local token cache for that client, or split its quota across sub-keys.
- **Clock skew between gateways:** pass time from one source (Redis `TIME`) or tolerate small skew; never use a wall clock that can jump backwards.
- **Redis failure:** fail open with local per-instance limits as a fallback, and alert.
- **Accuracy vs cost:** sliding log is exact but memory-heavy; counters are approximate but O(1).

## Scaling Strategy
1. **Single instance, in-memory** (this repo). Fine for one server.
2. **Multiple instances + one Redis** with the Lua script. Handles tens of thousands of checks/sec.
3. **Redis Cluster** sharded by client key once one node's throughput or memory is exceeded.
4. **Hybrid local + global:** local token caches with periodic Redis sync for very high QPS.
5. **Multi-region:** limit per region (simplest) or replicate counters asynchronously and accept some overshoot.
