# Web Crawler: Design Summary

## Problem Statement
A search engine (or an archive, or a price-monitoring tool) needs copies of web pages. A crawler starts from seed URLs, downloads pages, extracts links, and repeats, while being polite to websites, avoiding duplicate work, and scaling to billions of pages.

## Functional Requirements
1. Crawl outward from seed URLs by following links.
2. Store downloaded pages for downstream indexing.
3. Respect robots.txt and per-site rate limits (politeness).
4. Avoid re-fetching the same URL and re-processing duplicate content.
5. Re-crawl pages periodically to keep them fresh.

## Non-Functional Requirements
- **Scale (assumed):** 1B pages/month ≈ 400 pages/s average.
- **Politeness:** never more than ~1 request/second to a single host (configurable).
- **Robustness:** survive malformed HTML, spider traps, slow or huge responses, and worker crashes.
- **Extensibility:** pluggable content processors (images, PDFs) and prioritization.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **URL Frontier:** the to-do list. Front queues order URLs by **priority** (PageRank, freshness); back queues are **one per host**, so each host is fetched by one worker at a time with a minimum delay.
- **Fetcher workers** take polite URLs, resolve DNS (cached), download with timeouts and size limits, and store raw pages in blob storage.
- **Parser** extracts links and text; a **content hash** check skips pages identical to ones already seen (mirrors, `/index.html` vs `/`).
- **URL filter** normalizes links, enforces scope, checks the **robots.txt cache**, and drops URLs already in the **seen-URL Bloom filter**. New URLs go back into the frontier.

## API Design
Mostly internal. Key interfaces:
```java
FetchedPage fetch(URI url);                       // fetcher
Optional<Entry> next(long nowMillis);             // frontier: next URL whose host may be contacted
void add(URI url, int depth);                     // frontier: enqueue
CrawlResult crawl(List<URI> seeds);               // the loop
```
An admin API might expose `POST /crawls {seeds, scope, maxDepth}` and `GET /crawls/{id}/stats`.

## Data Model
| Data | Store | Notes |
|---|---|---|
| Frontier queues | Kafka / Redis lists, partitioned by host hash | Persisted so a crash doesn't lose the to-do list |
| Seen URLs | Bloom filter (in memory) + RocksDB/Cassandra keyed by URL hash | Bloom filter answers "definitely new" cheaply |
| Content hashes | Key-value store: `sha256 → first URL` | Exact-duplicate detection |
| Raw pages | S3 / HDFS: `url_hash → compressed HTML + headers + fetch time` | Input for indexing |
| Host metadata | `host → robots rules, last fetch, crawl delay, IP` | Cached in workers |

## Key Design Decisions
| Decision | Why |
|---|---|
| **BFS over DFS** | BFS finds important, well-linked pages early and avoids diving into deep traps. |
| **Per-host queues** | Politeness by construction: one host, one queue, one worker, a minimum delay. |
| **URL normalization** | Without it, `HTTP://A.com:80/x#y` and `http://a.com/x` are fetched twice. |
| **Bloom filter for seen URLs** | Billions of URLs don't fit in a hash set; a Bloom filter uses ~10 bits per URL with tunable false positives (skipping a few new URLs is acceptable). |
| **Content hashing** | Many URLs serve identical content; processing them again wastes bandwidth and pollutes the index. |
| **Check robots at discovery** | Blocked URLs never enter the frontier or consume a politeness slot. |

## Bottlenecks and Trade-offs
- **Spider traps** (infinite calendars, session IDs in URLs): cap depth, URL length, and pages per host; strip session parameters.
- **DNS latency:** DNS lookups can dominate fetch time, so cache resolutions per worker.
- **Slow or huge responses:** strict timeouts and max response size; stream and abort.
- **Hot hosts** (one site with millions of pages): politeness limits throughput per host; spread work across many hosts to keep workers busy.
- **Freshness vs coverage:** re-crawl budget must be split between revisiting known pages (news sites often, static pages rarely) and discovering new ones.

## Scaling Strategy
1. **Single-threaded crawler** with in-memory frontier (this repo).
2. **Thread pool** in one process; persist the frontier and seen set to disk.
3. **Distributed workers:** partition hosts across workers by `hash(host)`, so each host's politeness is enforced in one place. Frontier in Kafka/Redis.
4. **Bloom filters + sharded stores** for seen URLs and content hashes; blob storage for pages.
5. **Geo-distributed fetchers** close to the sites they crawl; priority and re-crawl scheduling driven by page importance and change rate.
