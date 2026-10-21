# News Feed System: Interview Questions

**1. Estimate read and write load.**
Assume 300M DAU × 10 feed loads/day = 3B reads/day ≈ **35k reads/s** (peak ~100k/s). Writes: 50M posts/day ≈ **600 posts/s**. Reads dominate by ~60×, so optimize the read path.

**2. Fan-out on write vs fan-out on read?**
Push: precompute each follower's feed at post time, which gives fast reads but costs O(followers) writes per post. Pull: compute at read time by querying all followees, which gives cheap writes but slow reads for users following many accounts. Most real systems use a hybrid.

**3. What's the "celebrity problem" and how do you solve it?**
A user with 50M followers would trigger 50M cache writes per post. That takes minutes and wastes work for inactive followers. Solution: don't fan out for accounts above a follower threshold; followers pull their recent posts at read time and merge.

**4. Estimate fan-out write volume.**
600 posts/s × ~200 average followers = **120k feed writes/s** into Redis. That needs a sharded Redis cluster and an async worker fleet. Without the celebrity exception the average would be far higher because of the long tail.

**5. Why store post IDs instead of full posts in the feed cache?**
Memory (one copy per post instead of one per follower), and edits or deletions update one record. Hydration is a batch `MGET` from the post cache.

**6. How do you paginate a feed that keeps changing?**
Cursor-based pagination: "give me 20 posts older than ID X". Offsets (`page=3`) skip or duplicate posts when new ones arrive at the top. Time-ordered IDs make the cursor simply the last ID seen.

**7. How are post IDs generated?**
Snowflake-style: 41 bits of timestamp + machine ID + sequence. They're unique without coordination, roughly time-sorted, and fit in a 64-bit integer, so sorting by ID is sorting by time.

**8. What happens when someone follows or unfollows a user?**
Follow: backfill the followee's recent posts into the follower's cache (or rely on the next pull). Unfollow: filter at read time, and optionally clean the cache asynchronously. Don't block the follow action on cache maintenance.

**9. How would you add ranking instead of chronological order?**
Two stages. **Candidate generation** (pushed IDs + celebrity pulls + suggestions, a few hundred items), then **scoring** with a model using features like author affinity, engagement, recency, and media type. Precompute features; cache ranked results briefly.

**10. Do you fan out to inactive users?**
No. Skip users who haven't been active in, say, 30 days. When they return, build their feed by pulling, then resume pushes. This saves a lot of writes and memory.

**11. A fan-out worker crashes halfway through a post's followers. What happens?**
Kafka redelivers the event (offset not committed), and the worker re-runs the fan-out. `ZADD` with the same post ID is idempotent, so repeated writes are harmless.

**12. How do you handle a deleted post?**
Mark it deleted in the posts store and evict it from the post cache. Feed caches still hold the ID, but hydration skips deleted posts. Clean up IDs lazily.

**Likely follow-ups:** how to show "new posts available" (poll or push a counter) · feeds with ads (insert at ranking stage) · privacy (filter by visibility at hydration) · how Instagram/Twitter actually do it (both hybrid).
