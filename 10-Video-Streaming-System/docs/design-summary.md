# Video Streaming System: Design Summary

## Problem Statement
Creators upload videos in arbitrary formats; viewers watch on phones, TVs, and laptops over networks ranging from 2G to fibre. The system must convert each upload into formats every device can play, deliver it globally with minimal buffering, and do so at a scale where bandwidth is the dominant cost.

## Functional Requirements
1. Upload a video (large files, resumable).
2. Transcode into multiple resolutions and bitrates.
3. Stream with adaptive quality based on the viewer's network.
4. Show video metadata (title, status, view counts).

## Non-Functional Requirements
- **Smooth playback:** fast start (< 2 s), minimal rebuffering.
- **Global low latency:** content served from close to the viewer.
- **Scale (assumed):** 500 hours of video uploaded per minute; billions of views per day.
- **Durability** of originals and transcoded files; **cost efficiency** (egress bandwidth dominates).
- **Availability** of playback matters more than of upload.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).

**Upload + processing (asynchronous):**
- The client gets a **presigned URL** and uploads the original straight to object storage (resumable, multipart), bypassing app servers.
- The upload service splits the work into **jobs, one per (segment, rendition)**, and queues them.
- **Transcoding workers** (FFmpeg/GPU) process jobs in parallel and write segments plus playlists to the origin bucket. When the last job finishes, the video is marked **READY**.

**Playback:**
- The player loads the video page (metadata), then the **master playlist** from the CDN, which lists renditions with bandwidths.
- Before each segment, the player's **ABR** logic picks a rendition from measured throughput and buffer level, then fetches that rendition's next segment from the **CDN**. On a cache miss, the CDN fetches from the origin.

## API Design
```http
POST /videos                     { "title": "...", "sizeBytes": 734003200 }
→ 201 { "videoId": "v1", "uploadUrl": "https://storage/...presigned...", "status": "UPLOADING" }

GET  /videos/v1                  → 200 { "title": "...", "status": "READY", "manifestUrl": "https://cdn/v1/master.m3u8" }
GET  https://cdn/v1/master.m3u8              (HLS master playlist)
GET  https://cdn/v1/720p/playlist.m3u8       (media playlist)
GET  https://cdn/v1/720p/seg_0.ts            (segment)
```

## Data Model
| Data | Store | Notes |
|---|---|---|
| Video metadata | SQL (sharded by `video_id`) + cache | `id`, `owner`, `title`, `status`, `duration`, `renditions[]` |
| Original upload | Object storage (cold tier after processing) | Kept for re-transcoding with better codecs later |
| Segments + playlists | Object storage: `videos/{id}/{rendition}/seg_{n}.ts` | The CDN origin; immutable, so they cache forever |
| Transcode jobs | Queue + job table | `video_id`, `rendition`, `segment`, `status`, `attempts` |
| View counts | Counter service / stream aggregation | Approximate, eventually consistent |

## Key Design Decisions
| Decision | Why |
|---|---|
| **Segment into 2–10 s chunks** | Players can switch quality at segment boundaries; HTTP caches and CDNs handle small files well; parallel transcoding. |
| **HLS/DASH over HTTP** | Works through any CDN and firewall; no special streaming servers. |
| **Bitrate ladder** (240p…1080p/4K) | Every device and network has a suitable option. |
| **ABR on the client** | Only the player knows its real throughput and buffer; servers stay stateless. |
| **Parallel per-segment jobs** | A 2-hour film transcodes in minutes across many workers; a failed job retries one segment, not the whole video. |
| **CDN for delivery** | Egress from the origin is expensive and slow; edges cache popular segments near viewers (>95% hit rates for popular content). |

## Bottlenecks and Trade-offs
- **Transcoding cost:** CPU/GPU heavy. Transcode popular videos into more renditions and codecs (AV1), and rarely watched ones into fewer.
- **Storage multiplication:** every rendition is a full copy (~2–3× the original in total). Use tiered storage and drop unused renditions for cold videos.
- **Long-tail content** misses the CDN cache. Origin shield (a mid-tier cache) protects object storage.
- **Segment length:** short segments mean faster switching and start but more requests and overhead; long segments mean the opposite.
- **Viral video:** a sudden spike is mostly absorbed by the CDN. Pre-warm edges for planned premieres.

## Scaling Strategy
1. **Single server** that stores uploads and serves one MP4 (progressive download): no adaptation.
2. **Async transcoding** into an HLS ladder with workers and a queue; object storage for segments.
3. **CDN** in front of the origin; presigned direct uploads.
4. **Per-segment parallel jobs,** priority lanes (low rendition first so videos go live sooner), autoscaled GPU workers.
5. **Global:** multi-CDN, origin shields per region, popularity-based codec and rendition decisions, and ISP-embedded caches (like Netflix Open Connect).
