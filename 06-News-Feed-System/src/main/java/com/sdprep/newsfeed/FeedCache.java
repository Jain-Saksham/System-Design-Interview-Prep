package com.sdprep.newsfeed;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Each user's precomputed feed: post IDs, newest first, capped at {@code maxPostsPerUser}.
 * Stores IDs only; post content is fetched separately (and cached once, not once per follower).
 *
 * <p>Production: a Redis sorted set per user ({@code ZADD feed:{user} postId postId} + trim).
 */
public class FeedCache {

    private final int maxPostsPerUser;
    private final Map<String, NavigableSet<Long>> postIdsByUser = new ConcurrentHashMap<>();
    private final AtomicLong totalWrites = new AtomicLong();

    public FeedCache(int maxPostsPerUser) {
        this.maxPostsPerUser = maxPostsPerUser;
    }

    public void add(String userId, long postId) {
        NavigableSet<Long> feed = postIdsByUser.computeIfAbsent(userId,
                id -> new ConcurrentSkipListSet<>(Comparator.reverseOrder())); // newest (largest ID) first
        feed.add(postId);
        while (feed.size() > maxPostsPerUser) {
            feed.pollLast(); // drop the oldest; users rarely scroll that far, and it can be rebuilt
        }
        totalWrites.incrementAndGet();
    }

    /** Cached post IDs older than {@code beforeId}, newest first. */
    public List<Long> postIdsBefore(String userId, long beforeId) {
        NavigableSet<Long> feed = postIdsByUser.get(userId);
        return feed == null ? List.of() : List.copyOf(feed.tailSet(beforeId, false));
    }

    /** How many feed entries have been written in total. Shows the cost of fan-out on write. */
    public long totalWrites() {
        return totalWrites.get();
    }
}
