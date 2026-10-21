# File Storage System: Interview Questions

**1. Estimate storage for a large deployment.**
Assume 1B new objects/day, average 500 KB: 500 TB/day of logical data. With 3× replication: **1.5 PB/day** raw. Erasure coding (1.5× overhead) cuts that to 750 TB/day, which is why cold data is erasure-coded.

**2. Why separate metadata from data?**
They have opposite requirements. Metadata (key → chunk list) is small, needs strong consistency, and gets frequent lookups and listings. Data is huge, written once, and needs cheap capacity and high throughput. Separating them lets each use the right storage technology and scale independently.

**3. How do you achieve 11 nines of durability?**
Multiple copies across independent failure domains (racks, AZs), checksums to detect corruption, continuous scrubbing, and fast automatic repair, so the window in which a second or third failure could destroy all copies stays tiny. Durability is roughly a function of replica count and repair speed.

**4. Replication vs erasure coding?**
Replication (3 copies) gives simple, fast reads and 3× storage overhead. Erasure coding (e.g., 6 data + 3 parity fragments) survives 3 losses at 1.5× overhead, but reads and repairs need data from multiple nodes and CPU for decoding. Hot data usually uses replication, cold data erasure coding.

**5. In what order do you write data and metadata, and why?**
Data first, metadata last. If the server crashes after writing chunks but before metadata, the object is simply invisible and GC later removes the orphan chunks. The reverse order could expose metadata pointing to missing data.

**6. How do you detect and handle silent data corruption?**
Store a checksum per chunk (here the chunk ID is its SHA-256). Verify on every read; on mismatch, read another replica and schedule repair of the bad copy. Background scrubbers periodically re-read everything to catch corruption in rarely accessed data.

**7. How do you choose which nodes store a chunk?**
A placement algorithm that spreads replicas across failure domains and balances capacity: consistent or rendezvous hashing, or a placement service that tracks free space. Rendezvous hashing (used here) lets any server compute placement without a lookup table and minimizes movement when nodes change.

**8. How do you upload a 50 GB file?**
Multipart upload: initiate an upload ID, upload parts (e.g., 100 MB each) in parallel with independent retries, then "complete" with the list of part ETags, which atomically commits the metadata. Clients can resume after failures by re-sending only missing parts.

**9. How does listing by prefix work at scale?**
Keep keys sorted in the metadata index (range-partitioned), so `prefix=logs/2026/` is a range scan. Return results in pages (e.g., 1,000 keys) with a continuation token. Hash partitioning alone can't serve ordered prefix scans, so a separate ordered index is common.

**10. How do you provide read-after-write consistency?**
Serve reads from the authoritative metadata store (no stale caches or async metadata replicas on the read path), and commit metadata only after data is durable. An overwrite atomically switches the key to the new chunk list.

**11. What happens when a data node dies?**
Heartbeats stop; the cluster manager marks it dead after a timeout. Reads fail over to other replicas. Repair workers find every chunk that now has fewer than 3 copies (most-at-risk first) and copy them to healthy nodes, throttled so repair doesn't overwhelm the network.

**12. How is deletion handled if chunks can be shared (dedup)?**
Delete metadata immediately. Chunks are reclaimed by garbage collection once no object references them (reference counts or a periodic mark-and-sweep), with a grace period to protect in-flight uploads.

**Likely follow-ups:** presigned URLs for direct client uploads · storage classes and lifecycle rules (move to cold tier after 30 days) · object versioning and soft deletes · serving public files through a CDN.
