# Rate Limiter: Interview Questions

**1. Where should the rate limiter live: client, server, or gateway?**
Client-side limits can't be trusted. Server-side works but repeats in every service. The usual answer is the **API gateway or middleware**: one enforcement point that rejects early, before any backend cost. Individual services may add their own limits to protect expensive endpoints.

**2. What do you limit by?**
API key or user ID for authenticated traffic; IP address for anonymous traffic (careful: many users can share one IP behind NAT). Often combined: per user **and** per endpoint, plus a global cap.

**3. Compare token bucket and sliding window.**
Token bucket allows bursts up to capacity while enforcing an average rate, which suits most APIs. Sliding window enforces "at most N in any window" more strictly. The fixed window is simplest but allows 2× the limit across a boundary. See the table in [design-summary.md](design-summary.md#algorithms-compared).

**4. Estimate memory for 1M active clients.**
Token bucket needs ~2 numbers per client. In Redis, a small hash with key overhead is roughly 100 bytes, so 1M × 100 B ≈ **100 MB**. That fits one Redis node. A sliding *log* with 100 timestamps per client would be ~100× more.

**5. Can Redis keep up?**
Assume 50k requests/sec → 50k script calls/sec. A single Redis node handles on the order of 100k simple ops/sec, so one node may suffice. Shard by client key with Redis Cluster for headroom. (Benchmark your own scripts; the numbers vary with script size.)

**6. Why not just `GET` the count and `SET` it back from the app?**
It's a race. Two gateways read "99 of 100 used" concurrently and both allow, so the limit is exceeded. Use an atomic operation: a Lua script, or `INCR` with `EXPIRE` for a fixed window.

**7. How does the local code avoid race conditions?**
Each client's bucket is locked with `synchronized`, so check-and-decrement is atomic. Locking per bucket (not globally) means different clients never block each other. The concurrency tests fire 500 parallel requests and assert exactly `capacity` succeed.

**8. Redis is down. What happens?**
Choose deliberately. **Fail open** (allow traffic) keeps the API available and fits most rate limits. **Fail closed** (reject) fits security limits like login attempts or OTP sends. A middle ground: fall back to per-instance in-memory limits.

**9. How do clients know when to retry?**
Return `429` with `Retry-After` (seconds until a token is available) and `X-RateLimit-Limit` / `X-RateLimit-Remaining`. Well-behaved clients back off with jitter instead of hammering.

**10. How do you avoid a Redis round trip on every request at very high QPS?**
Give each gateway instance a local share of the budget and sync usage to Redis every few hundred milliseconds. You trade accuracy (brief overshoot) for latency. Also: keep Redis in the same AZ and use connection pooling.

**11. How do you change limits without a redeploy?**
Store rules in a config service or DB. Gateways cache them and refresh periodically or on a push notification. Include a version so changes roll out predictably.

**12. Why use a monotonic clock?**
`System.currentTimeMillis()` can jump backward or forward when the OS syncs time (NTP). A backward jump would make elapsed time negative and corrupt refills. `System.nanoTime()` only moves forward. In Redis, use the server's `TIME` so all gateways share one clock.

**Likely follow-ups:** rate limiting a distributed job queue (use a global token bucket) · limiting by cost instead of count (expensive endpoints spend more tokens) · DDoS protection (different layer: CDN or network edge, not app-level limits) · limiting outbound calls to a third-party API.
