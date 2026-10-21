# System Design Interview Prep

A personal handbook for the 20 most commonly asked system design interview problems. Each problem is a self-contained module with:

- **Working Java code:** a small, readable implementation you can run and test locally.
- **An architecture diagram:** Mermaid source plus a rendered SVG of the production design.
- **Concise docs:** a design summary (requirements → API → data → trade-offs → scaling) and 8–12 interview questions with short answers.

> **Local code vs production design.** The code runs in one process, mostly with in-memory storage, so you can read and test it in minutes. The docs and diagrams describe the production architecture you'd discuss in an interview. Each module's README has a table showing exactly what differs. The local code never claims distributed guarantees it doesn't have.

---

## Problems

| # | Problem | What you learn | Difficulty | Stack |
|---|---|---|---|---|
| 01 | [URL Shortener](01-URL-Shortener/README.md) | ID generation, Base62, read-heavy caching, redirects | Beginner | Spring Boot |
| 02 | [Rate Limiter](02-Rate-Limiter/README.md) | Token bucket, sliding window, atomic counters in Redis | Beginner | Plain Java |
| 03 | [API Gateway](03-API-Gateway/README.md) | Routing, auth, request aggregation, cross-cutting concerns | Intermediate | Plain Java |
| 04 | [Notification System](04-Notification-System/README.md) | Fan-out, queues, retries, multi-channel delivery | Intermediate | Plain Java |
| 05 | [Chat Messaging System](05-Chat-Messaging-System/README.md) | WebSockets, message ordering, presence, delivery receipts | Advanced | Plain Java |
| 06 | [News Feed System](06-News-Feed-System/README.md) | Fan-out on write vs read, ranking, timeline caching | Advanced | Plain Java |
| 07 | [Web Crawler](07-Web-Crawler/README.md) | URL frontier, politeness, deduplication, distributed workers | Intermediate | Plain Java |
| 08 | [Search Autocomplete](08-Search-Autocomplete/README.md) | Tries, top-k suggestions, prefix caching | Intermediate | Plain Java |
| 09 | [File Storage System](09-File-Storage-System/README.md) | Object storage, chunking, metadata vs blob separation | Advanced | Plain Java |
| 10 | [Video Streaming System](10-Video-Streaming-System/README.md) | Transcoding pipeline, adaptive bitrate, CDN | Advanced | Plain Java |
| 11 | [Ride Sharing System](11-Ride-Sharing-System/README.md) | Geospatial indexing, driver matching, location updates | Advanced | Plain Java |
| 12 | [Food Delivery System](12-Food-Delivery-System/README.md) | Order lifecycle, dispatch, real-time tracking | Intermediate | Plain Java |
| 13 | [E-commerce System](13-E-commerce-System/README.md) | Catalog, cart, checkout, inventory consistency | Intermediate | Spring Boot |
| 14 | [Payment System](14-Payment-System/README.md) | Idempotency, double-entry ledger, reconciliation | Advanced | Spring Boot |
| 15 | [Ticket Booking System](15-Ticket-Booking-System/README.md) | Seat locking, concurrency, holds with expiry | Intermediate | Plain Java |
| 16 | [Distributed Cache](16-Distributed-Cache/README.md) | Eviction (LRU), consistent hashing, replication | Intermediate | Plain Java |
| 17 | [Logging and Monitoring System](17-Logging-and-Monitoring-System/README.md) | Ingestion pipeline, aggregation, alerting | Intermediate | Plain Java |
| 18 | [Job Scheduler](18-Job-Scheduler/README.md) | Delayed and recurring jobs, leases, at-least-once execution | Intermediate | Plain Java |
| 19 | [Cloud File Synchronization](19-Cloud-File-Synchronization/README.md) | Chunk diffing, sync conflicts, change notifications | Advanced | Plain Java |
| 20 | [Distributed Message Queue](20-Distributed-Message-Queue/README.md) | Partitions, offsets, consumer groups, durability | Advanced | Plain Java |

Spring Boot is used only where an HTTP API is central to the problem; everything else is plain Java with a runnable `*Demo` class.

---

## Suggested Learning Order

Each step builds on ideas from the previous ones:

1. **Foundations:** 01 URL Shortener → 02 Rate Limiter → 16 Distributed Cache
2. **Infrastructure building blocks:** 03 API Gateway → 18 Job Scheduler → 20 Distributed Message Queue
3. **Async pipelines:** 04 Notification System → 17 Logging and Monitoring → 07 Web Crawler
4. **Search and feeds:** 08 Search Autocomplete → 06 News Feed
5. **Transactions and consistency:** 13 E-commerce → 15 Ticket Booking → 14 Payment System
6. **Real-time and location:** 05 Chat Messaging → 12 Food Delivery → 11 Ride Sharing
7. **Large files and media:** 09 File Storage → 19 Cloud File Synchronization → 10 Video Streaming

---

## Completion Checklist

Tick a problem once you can explain its design from the diagram alone and answer its interview questions without notes.

- [ ] 01 URL Shortener
- [ ] 02 Rate Limiter
- [ ] 03 API Gateway
- [ ] 04 Notification System
- [ ] 05 Chat Messaging System
- [ ] 06 News Feed System
- [ ] 07 Web Crawler
- [ ] 08 Search Autocomplete
- [ ] 09 File Storage System
- [ ] 10 Video Streaming System
- [ ] 11 Ride Sharing System
- [ ] 12 Food Delivery System
- [ ] 13 E-commerce System
- [ ] 14 Payment System
- [ ] 15 Ticket Booking System
- [ ] 16 Distributed Cache
- [ ] 17 Logging and Monitoring System
- [ ] 18 Job Scheduler
- [ ] 19 Cloud File Synchronization
- [ ] 20 Distributed Message Queue

---

## Running the Code

**Requirements:** JDK 21+ and Maven 3.9+. No database, Redis, Kafka, or Docker is needed to run any module locally.

```bash
# Build and test all 20 modules (231 tests) from the repo root
mvn test

# Or work inside one module
cd 01-URL-Shortener
mvn test
```

Each module's README has its own run command (a Spring Boot server or a plain `main()` demo) with sample input and output.

## Viewing and Editing Diagrams

- **View:** open `architecture/architecture.svg` in any module. It also renders inline in that module's README on GitHub and in IDE Markdown previews.
- **Edit:** change `architecture/architecture.mmd`, then re-render. See [common/README.md](common/README.md#diagrams) for the one-time setup and the shared visual style.

```bash
(cd common/diagrams && npm install)          # one-time: installs the pinned Mermaid CLI
common/diagrams/render-diagrams.sh            # re-render all diagrams
common/diagrams/render-diagrams.sh 02-Rate-Limiter   # or just one module
```

## Repository Layout

```
System-Design-Interview-Prep/
├── README.md              ← you are here
├── pom.xml                ← aggregator: `mvn test` runs every module
├── common/                ← conventions, diagram style and rendering tools
└── NN-Problem-Name/
    ├── README.md          ← overview, local vs production, how to run
    ├── architecture/      ← architecture.mmd (source) + architecture.svg (rendered)
    ├── docs/              ← design-summary.md, interview-questions.md
    ├── src/main, src/test ← Java implementation and JUnit tests
    └── pom.xml            ← standalone Maven build
```
