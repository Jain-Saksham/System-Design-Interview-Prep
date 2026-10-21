# Video Streaming System: Interview Questions

**1. Estimate upload storage per day.**
Assume 500 hours uploaded per minute → 720k hours/day. At ~1 GB/hour for the original plus ~2 GB/hour across all renditions: 720k × 3 GB ≈ **2.2 PB/day** before replication. Storage tiering and pruning renditions for unpopular videos matter.

**2. Estimate egress bandwidth.**
Assume 1B views/day × 10 minutes × ~3 Mbps average: 1B × 600 s × 3 Mb = 1.8 × 10¹² Mb/day ≈ **21 Tbps** average. That's why a CDN (and ISP-embedded caches) is mandatory, and why egress dominates cost.

**3. Why transcode at all?**
Uploads arrive in many codecs, containers, and resolutions. Devices support specific codecs (H.264 everywhere, HEVC/AV1 on newer devices), and networks vary. Transcoding produces a standard ladder of renditions so every viewer gets something playable at a suitable bitrate.

**4. Why split videos into segments?**
Players can switch quality at any segment boundary, segments are small cacheable HTTP objects, transcoding parallelizes per segment, and seeking only needs the target segment, not the whole file.

**5. What's in an HLS master playlist vs a media playlist?**
The master lists every rendition with `BANDWIDTH` and `RESOLUTION` and points to each rendition's media playlist. A media playlist lists that rendition's segments in order with durations (`#EXTINF`), plus `#EXT-X-ENDLIST` for on-demand videos.

**6. How does adaptive bitrate work?**
Before each segment, the player estimates throughput from recent downloads, applies a safety margin (e.g., 80%), and checks its buffer. It picks the highest rendition it can sustain, drops immediately if bandwidth falls or the buffer runs low, and climbs one level at a time only with a healthy buffer to avoid flicker. Modern players (BOLA, MPC) mix throughput and buffer signals.

**7. How do you make the transcoding pipeline reliable?**
Durable queue, idempotent jobs (output path is deterministic, so re-running overwrites the same segment), retries with backoff, a dead-letter queue for poison inputs, and a per-video job counter (or DAG) that marks READY only when every job succeeds.

**8. How do you get videos playable sooner after upload?**
Prioritize: transcode the lowest renditions (and the first segments) first, so the video can go live at 360p within seconds while higher qualities finish in the background.

**9. How should large uploads work?**
Presigned URLs for direct-to-storage uploads (app servers never proxy gigabytes), multipart and resumable uploads so a dropped connection resumes from the last part, and checksums per part.

**10. How does the CDN interact with the origin?**
Segments are immutable, so they get long cache TTLs. On a miss, the edge fetches from an origin shield (regional cache), which fetches from object storage. Popular content stays at the edge; long-tail content is served from the shield or origin.

**11. How would live streaming differ?**
Ingest via RTMP/SRT to a live transcoder that produces segments in real time. Playlists are rolling windows without `ENDLIST`, and players poll them. Low-latency HLS uses smaller partial segments. There's less time for retries, so redundancy happens at ingest.

**12. How do you count views at this scale?**
Don't update a DB row per view. Emit events to a stream, aggregate in windows (Flink/Spark), and update counters periodically. Approximate, eventually consistent counts are acceptable; deduplicate by session to resist inflation.

**Likely follow-ups:** DRM and signed URLs · thumbnail and preview generation · recommendations (separate system) · choosing codecs (AV1 saves bandwidth but costs more to encode).
