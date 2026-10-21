# Web Crawler: Interview Questions

**1. Estimate throughput and storage.**
Assume 1B pages/month: 1B / 2.6M s ≈ **400 pages/s** (plan for ~2× peak). At ~100 KB per page: 1B × 100 KB = **100 TB/month** raw, maybe 25–30 TB compressed. Five years of history is petabytes, so use blob storage, not a database.

**2. BFS or DFS?**
BFS. It reaches important, highly linked pages early, spreads load across many hosts, and avoids getting lost in deep link chains (spider traps). DFS can go infinitely deep on a single site.

**3. How do you enforce politeness?**
One queue per host, consumed by a single worker, with a minimum delay between requests (e.g., 1 s, or the site's `Crawl-delay`). Respect robots.txt. Back off when a site returns 429/503 or responds slowly.

**4. How do you avoid fetching the same URL twice?**
Normalize URLs (lowercase scheme and host, drop default ports and fragments, resolve relative paths, sort or strip tracking params), then check a seen-URL set. At billions of URLs, use a Bloom filter backed by a persistent store.

**5. Why a Bloom filter, and what's the trade-off?**
It answers "definitely not seen" or "probably seen" using a few bits per item (~1.2 GB for 1B URLs at 1% false positives). A false positive means occasionally skipping a genuinely new URL. That's acceptable for a crawler; the alternative is a hash set many times larger.

**6. How do you detect duplicate content?**
Hash the page body (SHA-256) for exact duplicates. For near-duplicates (same article, different ads or timestamps), use SimHash or MinHash fingerprints and compare Hamming distance.

**7. What is a spider trap and how do you defend against it?**
A site that generates infinite URLs (calendars with "next month" forever, session IDs). Defences: max depth, max URL length, max pages per host, strip session parameters, detect repeating path patterns.

**8. How do you distribute the crawler across many machines?**
Partition by host: `worker = hash(host) mod N`. Each host lives on exactly one worker, which enforces its politeness locally. The frontier is a partitioned queue (Kafka partitions by host hash); the seen-URL store is sharded by URL hash.

**9. How do you prioritize which URLs to crawl first?**
Front queues ranked by priority: PageRank or inbound link count, domain authority, freshness need, and how recently the URL was discovered. A selector pulls more from high-priority queues while back queues still enforce politeness.

**10. How do you keep pages fresh?**
Re-crawl based on observed change rate. If a page changed on each of the last visits, visit more often; if it never changes, back off. Use HTTP `If-Modified-Since`/`ETag` to avoid re-downloading unchanged pages. Sitemaps give hints.

**11. A worker crashes mid-crawl. What's lost?**
Nothing important if the frontier is persisted: un-acked URLs are redelivered to the host's new owner. Re-fetching a few pages is harmless because storage writes are idempotent (keyed by URL hash).

**12. How do you handle robots.txt efficiently?**
Fetch it once per host, cache it with a TTL (e.g., 24 h), and check rules when URLs are discovered so blocked URLs never enter the frontier. If robots.txt returns 5xx, be conservative and pause crawling that host.

**Likely follow-ups:** crawling JavaScript-rendered pages (headless browsers, very expensive) · handling redirects and canonical tags · respecting `noindex`/`nofollow` · how to measure crawler health (pages/s, error rate per host, frontier size).
