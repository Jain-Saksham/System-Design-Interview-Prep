# News Feed System: Design Summary

## Problem Statement
Users follow other users and expect a feed of their latest posts, loading instantly and scrolling smoothly. Reads vastly outnumber writes, and follower counts are extremely skewed: most users have hundreds of followers, a few have tens of millions.

## Functional Requirements
1. Publish a post.
2. Follow and unfollow users.
3. Fetch a feed of posts from followed users, newest first (or ranked).
4. Paginate with infinite scroll.

## Non-Functional Requirements
- **Fast feed reads:** p99 < 200 ms; reads ≫ writes.
- **Freshness:** new posts appear within seconds (eventual consistency is fine).
- **Scale (assumed):** 300M DAU, each loading the feed ~10×/day; 50M posts/day.
- **Availability:** a stale feed is better than no feed.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).

**Write path:** the post service saves the post and publishes an event to Kafka. Fan-out workers look up the author's followers and `ZADD` the post ID into each follower's Redis feed (trimmed to ~500 entries). **Celebrities are skipped.**

**Read path:** the feed service reads the user's cached post IDs, pulls recent posts from the celebrities the user follows, merges and deduplicates them, ranks (or sorts by ID), and hydrates content from the post cache. It returns one page and a cursor.

| Strategy | Write cost | Read cost | Good for |
|---|---|---|---|
| Fan-out on write (push) | O(followers) per post | O(1) cache read | Regular users |
| Fan-out on read (pull) | O(1) | O(followees) queries per read | Celebrities, inactive users |
| **Hybrid** ✅ | Push for most, pull for celebrities | Cache read + a few pulls | Real systems |

## API Design
```http
POST /posts          { "text": "Coffee anyone?" }              → 201 { "id": 3 }
POST /follows        { "followeeId": "bob" }                   → 204
GET  /feed?limit=20&cursor=1820349                             →
200 { "posts": [ {"id": 1820348, "authorId": "bob", "text": "...", "createdAt": "..."} ],
      "nextCursor": 1820329 }
```
Cursor = the last post ID seen. Unlike offset pagination, it doesn't skip or repeat posts when new ones arrive at the top.

## Data Model
| Store | Key | Value |
|---|---|---|
| Posts (Cassandra/MySQL) | `post_id` (Snowflake: time-ordered) | `author_id`, `text`, `media_urls`, `created_at` |
| Posts by author | `author_id`, clustered by `post_id` DESC | (for celebrity pulls and profile pages) |
| Follows | `(follower_id, followee_id)` + reverse index | `created_at` |
| Feed cache (Redis) | `feed:{user_id}` sorted set | post IDs scored by ID/time, max ~500 |
| Post cache (Redis) | `post:{post_id}` | serialized post (cached once, shared by all readers) |

The feed cache stores **IDs, not content**: a post fanned out to 1,000 feeds is stored once, and edits or deletes don't touch 1,000 entries.

## Key Design Decisions
| Decision | Why |
|---|---|
| **Hybrid fan-out** | Push makes reads cheap, but one celebrity post would mean millions of writes. Pull for those few accounts. |
| **Async fan-out via Kafka** | Publishing returns instantly; workers absorb spikes and retry failures. |
| **Time-ordered IDs** | Sort and paginate by ID; no separate timestamp index; cursors are just IDs. |
| **Cap cached feeds (~500)** | Bounded memory; deep scrolls fall back to pull from the DB. |
| **Lazy unfollow** | Filter unfollowed authors at read time rather than scanning caches to delete. |
| **Skip fan-out for inactive users** | No point precomputing feeds nobody reads; build on login instead. |

## Bottlenecks and Trade-offs
- **Celebrity write amplification:** solved by pulling; the threshold (e.g., 10k–100k followers) is tunable.
- **Fan-out lag:** followers of a user with 9k followers see posts a few seconds late. Acceptable (eventual consistency).
- **Hot posts:** a viral post is read millions of times, so the post cache and CDN for media carry the load.
- **Memory:** 300M users × 500 IDs × 8 bytes ≈ 1.2 TB of Redis. Only cache active users; rebuild on demand.
- **Ranking cost:** scoring hundreds of candidates per request needs precomputed features and a fast model.

## Scaling Strategy
1. **Pull-only from a single DB** (`SELECT ... WHERE author IN (followees) ORDER BY id DESC`): simple, fine for small scale.
2. **Fan-out on write into Redis;** feeds become cache reads.
3. **Hybrid** for celebrities; fan-out moves to async workers via Kafka.
4. **Shard** posts, follows, and feed caches by user ID; hydrate from a post cache.
5. **Ranking service** with ML models; separate candidate generation from scoring; multi-region caches.
