# File Storage System: Design Summary

## Problem Statement
Applications need to store files of any size (photos, videos, backups, logs) without managing disks. An object store offers a simple key-based API (`PUT bucket/key`, `GET bucket/key`) with extreme durability ("eleven nines"), near-unlimited capacity, and high throughput, like Amazon S3.

## Functional Requirements
1. Create buckets; put, get, delete objects by key.
2. List objects by key prefix (e.g., `logs/2026/`).
3. Support objects from bytes to terabytes (multipart upload).
4. Return integrity information (ETag/checksum) for each object.

## Non-Functional Requirements
- **Durability:** 99.999999999% (no data loss when disks and machines fail).
- **Availability:** 99.9%+ for reads and writes.
- **Scalability:** exabytes of data, trillions of objects, high aggregate throughput.
- **Consistency:** read-after-write for new objects and overwrites (S3 has offered strong consistency since 2020).
- **Cost:** storage must be cheap per GB, which drives replication vs erasure-coding choices.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **API servers** authenticate requests, split uploads into chunks, compute checksums, and coordinate reads and writes. They're stateless.
- **Data tier:** data nodes store chunks on local disks. Each chunk is replicated (3 copies across racks or zones) or erasure-coded.
- **Metadata tier:** a strongly consistent, sharded database mapping `bucket + key → size, etag, chunk list, version`.
- **Cluster manager** tracks node health (heartbeats) so placement avoids dead nodes.
- **Background workers** re-replicate chunks after failures, garbage-collect unreferenced chunks, and **scrub** (re-verify checksums) to catch silent disk corruption.

**Write path:** chunk → write all replicas → commit metadata (the object becomes visible only at this point). **Read path:** metadata lookup → read each chunk from a healthy replica → verify checksum → stream to client.

## API Design
```http
PUT    /photos/2026/cat.jpg          (body = bytes)   → 200, ETag: "ddf71c0f..."
GET    /photos/2026/cat.jpg                           → 200 (bytes), ETag
HEAD   /photos/2026/cat.jpg                           → 200, Content-Length, ETag (no body)
DELETE /photos/2026/cat.jpg                           → 204
GET    /photos?prefix=2026/&max-keys=1000             → 200 { "keys": [...], "nextContinuationToken": "..." }
POST   /photos/big.mp4?uploads                        → multipart: initiate, upload parts in parallel, complete
```

## Data Model
**Metadata (sharded by `hash(bucket, key)`; prefix listing uses a range-partitioned index):**

| Field | Example |
|---|---|
| `bucket`, `key` | `photos`, `2026/cat.jpg` |
| `size`, `etag`, `content_type` | `36`, `ddf71c...`, `image/jpeg` |
| `version` | `1` (or a version ID if versioning is enabled) |
| `chunks` | `[ {chunk_id, size, replicas: [node-4, node-2, node-5]} ]` |

**Data:** each chunk is stored as a file named by its ID on each replica node. Content-addressed IDs (SHA-256) give free deduplication and built-in checksums.

## Key Design Decisions
| Decision | Why |
|---|---|
| **Separate metadata and data** | Metadata is tiny and needs strong consistency and fast lookups; data is huge and needs cheap capacity. Different stores, scaled independently. |
| **Chunking** | Large objects spread across many disks (parallel reads/writes), and failures only affect pieces. |
| **Commit metadata last** | A crash mid-upload leaves orphan chunks (cleaned by GC), never a visible half-object. |
| **Checksums on every read** | Disks silently corrupt data; a mismatch triggers reading another replica and repairing the bad one. |
| **Replicate across failure domains** | Copies on different racks/zones survive rack power loss or a zone outage. |
| **Erasure coding for cold data** | 6+3 coding survives 3 failures at 1.5× storage overhead versus 3× for replication, at the cost of more CPU and slower reads. |

## Bottlenecks and Trade-offs
- **Metadata hotspot:** sequential keys (`logs/2026-01-01-00001`) concentrate on one shard. Hash-partition keys, or add random prefixes for very hot buckets.
- **Small files:** per-object metadata overhead dominates. Pack small objects into larger chunks.
- **Replication vs erasure coding:** speed and simplicity vs storage cost.
- **Write latency:** waiting for all replicas is slower; a write quorum (2 of 3) plus async repair is faster but needs careful handling.
- **Repair storms:** when a big node dies, re-replicating terabytes can saturate the network. Throttle repair and prioritize chunks with the fewest remaining copies.

## Scaling Strategy
1. **Single server** storing files on disk, metadata in SQLite (fine for a team).
2. **Separate metadata DB + several data nodes** with 3× replication.
3. **Shard metadata,** add the cluster manager, background repair, scrubbing, and GC.
4. **Multi-AZ placement** and erasure coding for colder storage tiers.
5. **Multi-region:** asynchronous cross-region replication (opt-in per bucket), CDN in front for public reads.
