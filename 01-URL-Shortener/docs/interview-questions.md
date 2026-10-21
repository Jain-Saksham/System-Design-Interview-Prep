# URL Shortener: Interview Questions

**1. Estimate the scale. How many codes do we need, and how long should they be?**
Assume 100M new links/month for 5 years: 100M × 12 × 5 = **6B links**. Base62 with 7 characters gives 62⁷ ≈ **3.5 trillion** codes, which is far more than enough. 6 characters (≈ 57B) would also work but leaves less headroom.

**2. What's the read/write QPS, and what does it imply?**
Writes: 100M / 2.6M seconds per month ≈ **40/s**. With 100:1 reads ≈ **4,000/s** on average, maybe 5× that at peak. Writes are trivial; the design is about serving reads fast (caching, stateless horizontal scaling).

**3. How much storage?**
~500 bytes per row (URL + metadata + index overhead) × 6B ≈ **3 TB** over 5 years. One PostgreSQL node can hold this, but you'd shard for write headroom and operational comfort, or pick a key-value store.

**4. Hashing vs counter-based codes?**
Hashing (e.g., first 7 chars of MD5 of the URL) is stateless, but collisions must be detected and retried. A counter + Base62 is collision-free and short but needs a coordinated ID source, and its codes are sequential. Counter + ID block leasing is the common interview answer.

**5. How do multiple servers generate unique IDs without a bottleneck?**
Each server leases a range (say 10,000 IDs) from a DB sequence or ZooKeeper and serves it from memory. That's one coordination call per 10,000 links. Alternatives: Snowflake-style IDs (timestamp + machine ID + sequence), which produce longer codes.

**6. 301 or 302 redirect?**
301 (permanent) is cached by browsers, so it reduces load but loses analytics and makes expiry impossible for cached clients. 302 (temporary) sends every click through the service. Choose 302 when you need analytics or expiry.

**7. How do you design the cache?**
Cache-aside in Redis: `code → longUrl`, with LRU eviction and a TTL capped at the link's expiry. Popularity is skewed, so a cache holding a small fraction of links absorbs most reads. On a miss, read the DB and populate the cache. Negative-cache unknown codes briefly to blunt enumeration attacks.

**8. Two users request the same custom alias at the same time. What happens?**
The insert relies on the primary-key unique constraint: one `INSERT` succeeds and the other fails, and we return `409`. Never "check then insert" as two separate steps; that's a race. The local code mirrors this with `ConcurrentHashMap.putIfAbsent`.

**9. Should the same long URL always return the same code?**
Only if required. Deduplication needs an index on `long_url` (large, since URLs are long; usually you'd index a hash of it) and leaks information across users. Most shorteners create a new code per request, and per-user deduplication is optional.

**10. How do you track clicks without slowing redirects?**
Publish a click event (code, timestamp, referrer, country) to Kafka fire-and-forget, and return the 302 immediately. A consumer aggregates counts in batches into an analytics store. Counts become eventually consistent, which is acceptable for analytics.

**11. How do you handle expired links and cleanup?**
Check `expires_at` on read and return `410 Gone`. A scheduled job deletes expired rows in batches using an index on `expires_at`, and the cache TTL never outlives the link.

**12. What abuse and security concerns exist?**
Malicious destinations (check Safe Browsing or blocklists at creation, and optionally show an interstitial), `javascript:`/`data:` URLs (allow only http/https), enumeration of sequential codes (scramble IDs), and spam creation (rate-limit per IP or API key; see [Rate Limiter](../../02-Rate-Limiter/README.md)).

**Likely follow-ups:** custom domains per customer · link editing (cache invalidation) · multi-region reads · how to migrate when the DB must be sharded (shard by code hash; IDs from a global allocator).
