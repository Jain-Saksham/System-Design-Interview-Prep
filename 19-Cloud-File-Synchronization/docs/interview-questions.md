# Cloud File Synchronization: Interview Questions

**1. Estimate storage and metadata load.**
Assume 500M users × 10 GB average = **5 EB** logical, reduced meaningfully by dedup and compression (exact savings vary). Commits: 100M DAU × 2 changes/day ≈ **2,300 commits/s** average, with peaks several times higher. Metadata is tiny per file but very high in row count, so shard it.

**2. Why split files into chunks?**
Only changed chunks are uploaded or downloaded (delta sync), uploads resume per chunk after network failures, chunks spread across storage for parallelism, and identical chunks across files are stored once.

**3. Why name chunks by their hash?**
Content addressing: the same bytes always get the same name. That gives deduplication for free (check if the hash exists before uploading), an integrity check (re-hash on receipt), and immutability, so chunks can be cached forever.

**4. Fixed-size vs content-defined chunking?**
Fixed-size is simple, but inserting one byte shifts every later chunk boundary, so everything after the edit looks new. Content-defined chunking picks boundaries where a rolling hash matches a pattern, so boundaries move with the content and only nearby chunks change. It's used by Dropbox-style systems and backup tools.

**5. How does a device learn that something changed?**
A long-poll or WebSocket notification service signals "your journal advanced" (no data). The device then calls `changesSince(cursor)` on the metadata service, gets ordered changes, and downloads only missing chunks. Polling every few seconds also works at smaller scale.

**6. Two devices edit the same file offline. What happens?**
Each commit carries the base version it was edited from. The first commit wins (base matches current). The second gets CONFLICT, and the client saves its version as "file (conflicted copy from device).ext". Both edits survive; the user decides.

**7. Why not merge conflicting edits automatically?**
For arbitrary binary files (photos, spreadsheets), merging isn't defined. Even for text, silent merges can produce nonsense. Conflicted copies guarantee no data loss. Collaborative editors (Google Docs) use different techniques (OT/CRDTs) at the application level.

**8. How do you handle deletes so other devices notice?**
Write a tombstone version (`deleted = true`) to the journal. Other devices apply it when they pull. Without tombstones, an offline device couldn't distinguish "deleted on server" from "new file on this device" and might re-upload the deleted file.

**9. When can a chunk actually be deleted from storage?**
Only when no file version references it, across all files and users if dedup is global, and after the version-history retention period. Track reference counts or run periodic mark-and-sweep garbage collection.

**10. How do you make uploads reliable on flaky networks?**
Chunk-level retries and resume (upload only chunks still missing), commit only after all chunks are present (the server rejects commits referencing missing chunks), and idempotent operations (uploading the same chunk twice is harmless).

**11. How do you support version history and restore?**
Keep every `file_version` row (its chunk list) for a retention period. Restoring creates a new version pointing at the old chunk list; no data is copied because the chunks are still there.

**12. What changes if you add end-to-end encryption?**
Chunks are encrypted on the client with user-held keys, so the server can't read content. Cross-user dedup stops working (identical plaintext produces different ciphertext), server-side search and previews become impossible, and key management (recovery, sharing) becomes the hard part.

**Likely follow-ups:** sharing folders between users (namespaces and ACLs) · LAN sync between devices on the same network · bandwidth throttling on mobile · selective sync (download files on demand).
