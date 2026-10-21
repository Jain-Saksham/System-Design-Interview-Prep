# Cloud File Synchronization: Design Summary

## Problem Statement
Users want the same files on every device, edit them offline, and expect changes to appear everywhere within seconds without re-uploading entire files. With hundreds of millions of users, bandwidth and storage dominate cost, so moving and storing as little data as possible is the core of the design.

## Functional Requirements
1. Upload, update, and delete files in a synced folder.
2. Propagate changes to all of a user's devices automatically.
3. Work offline and sync when back online.
4. Never lose an edit; detect conflicting edits.
5. Keep version history (restore previous versions).

## Non-Functional Requirements
- **Bandwidth efficiency:** only changed parts of files are transferred.
- **Storage efficiency:** identical data is stored once (dedup).
- **Durability:** 11 nines for file content (object storage).
- **Consistency:** each file has a clear latest version; devices converge.
- **Scale (assumed):** 500M users, 100M daily active, ~2 file changes per user per day ≈ 2,300 commits/s average.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Client:** watches the folder, splits changed files into chunks (~4 MB), hashes each chunk (SHA-256), and keeps a local index of `path → version, chunk hashes`.
- **Upload:** ask the **block service** which hashes are missing, upload only those (into object storage, keyed by hash), then **commit** to the **metadata service** with the base version the edit started from.
- **Metadata service:** accepts the commit if the base version matches the current version (optimistic concurrency), writes the new version, and appends to the user's **change journal**. Otherwise it returns CONFLICT.
- **Conflict handling:** the losing client saves its content as a "conflicted copy" file, so both edits survive and the user resolves them.
- **Propagation:** the **notification service** (long poll/WebSocket) tells other devices that the journal advanced; they call `changesSince(cursor)`, download only chunks they don't have, and rebuild files.

## API Design
```http
POST /blocks/missing      { "hashes": ["ab12…", "cd34…"] }                 → { "missing": ["cd34…"] }
PUT  /blocks/cd34…        (chunk bytes; server verifies the hash)           → 201
POST /files/commit        { "path": "notes.txt", "baseVersion": 3, "chunks": ["ab12…","cd34…"], "size": 8_000_000 }
                          → 200 { "version": 4 } | 409 { "current": { "version": 4, "chunks": [...] } }
GET  /changes?cursor=1287 → { "changes": [ {path, version, chunks, deleted}, ... ], "cursor": 1290 }
GET  /notify?cursor=1290  (long poll; returns when the journal moves past the cursor)
```

## Data Model
| Store | Key | Value |
|---|---|---|
| Object storage | `chunk SHA-256` | compressed (and encrypted) chunk bytes |
| `files` (SQL, sharded by user/namespace) | (`namespace_id`, `path`) | `latest_version`, `size`, `deleted` |
| `file_versions` | (`namespace_id`, `path`, `version`) | ordered `chunk hashes`, `device_id`, `created_at`; enables history and restore |
| `journal` | (`namespace_id`, `sequence`) | (`path`, `version`): what changed, in order |
| Chunk refcounts | `chunk hash` | count of versions referencing it (for garbage collection) |

## Key Design Decisions
| Decision | Why |
|---|---|
| **Chunking** | A one-word edit in a 1 GB file uploads ~4 MB, not 1 GB; uploads resume per chunk. |
| **Content addressing (hash as chunk name)** | Free deduplication across files and users; built-in integrity check; chunks are immutable and cacheable forever. |
| **Separate block and metadata services** | Blocks are huge, immutable, and cheap (S3). Metadata is small, transactional, and hot (SQL). They scale differently. |
| **Optimistic concurrency with base versions** | No locking files across devices (offline editing). Conflicts are detected exactly when they happen. |
| **Conflicted copies instead of merging** | Arbitrary files (images, binaries) can't be merged; keeping both guarantees no data loss. |
| **Journal + cursors** | Devices ask "what changed since N?", which is cheap, resumable after being offline for weeks, and naturally ordered. |

## Bottlenecks and Trade-offs
- **Fixed-size vs content-defined chunking:** fixed chunks break when bytes are inserted (every following chunk shifts). Content-defined chunking (rolling hash boundaries) localizes changes at some CPU cost.
- **Small files:** per-file metadata overhead dominates. Batch small-file commits and pack small chunks.
- **Notification fan-out:** millions of long-poll connections need connection-optimized servers; notifications carry no data, just "sync now".
- **Hot metadata shard** for huge shared folders (a company-wide drive): split namespaces, cache read paths.
- **Deletes and GC:** chunks can only be deleted when no version references them. Refcounts or periodic mark-and-sweep, after the history retention window.
- **Encryption vs dedup:** client-side end-to-end encryption with per-user keys defeats cross-user dedup.

## Scaling Strategy
1. **Upload whole files** to one server with a metadata DB (simple, but bandwidth-heavy).
2. **Chunking + content addressing** on object storage; delta uploads and dedup.
3. **Journal + long-poll notifications**; offline-friendly clients with local indexes.
4. **Shard metadata by namespace;** block service behind a CDN; content-defined chunking; compression.
5. **Global:** regional block storage and edge upload endpoints; metadata homed per namespace region; LAN sync between nearby devices.
