package com.sdprep.newsfeed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Hybrid fan-out:
 * <ul>
 *   <li>Regular users: <b>fan-out on write</b>. A new post is pushed into every follower's cached feed,
 *       so reading a feed is just a cache read.</li>
 *   <li>Celebrities (many followers): <b>fan-out on read</b>. Pushing to millions of feeds per post is too
 *       expensive, so their recent posts are pulled and merged when a follower reads their feed.</li>
 * </ul>
 */
public class FeedService {

    private static final int POSTS_BACKFILLED_ON_FOLLOW = 20;

    private final PostStore posts;
    private final FollowGraph graph;
    private final FeedCache feedCache;
    private final int celebrityFollowerThreshold;
    private final LongSupplier clockMillis;
    private final AtomicLong nextPostId = new AtomicLong(1);

    public FeedService(PostStore posts, FollowGraph graph, FeedCache feedCache,
                       int celebrityFollowerThreshold, LongSupplier clockMillis) {
        this.posts = posts;
        this.graph = graph;
        this.feedCache = feedCache;
        this.celebrityFollowerThreshold = celebrityFollowerThreshold;
        this.clockMillis = clockMillis;
    }

    public Post publish(String authorId, String text) {
        Post post = new Post(nextPostId.getAndIncrement(), authorId, text, clockMillis.getAsLong());
        posts.save(post);
        feedCache.add(authorId, post.id()); // authors see their own posts
        if (!isCelebrity(authorId)) {
            // Production: done asynchronously by fan-out workers reading from a queue.
            graph.followersOf(authorId).forEach(followerId -> feedCache.add(followerId, post.id()));
        }
        return post;
    }

    public void follow(String followerId, String followeeId) {
        graph.follow(followerId, followeeId);
        if (!isCelebrity(followeeId)) {
            // Older posts were fanned out before this follow, so copy recent ones in now.
            posts.recentByAuthor(followeeId, Long.MAX_VALUE, POSTS_BACKFILLED_ON_FOLLOW)
                    .forEach(post -> feedCache.add(followerId, post.id()));
        }
    }

    /** Cached entries from unfollowed users are filtered out at read time instead of being deleted. */
    public void unfollow(String followerId, String followeeId) {
        graph.unfollow(followerId, followeeId);
    }

    /**
     * Newest-first page of the feed.
     *
     * @param cursor null for the first page, otherwise the previous page's {@code nextCursor}
     */
    public FeedPage getFeed(String userId, int limit, Long cursor) {
        long beforeId = cursor == null ? Long.MAX_VALUE : cursor;
        Set<String> followed = graph.followedBy(userId);

        // TreeMap keyed by post ID: merges both sources, removes duplicates, sorts newest first.
        NavigableMap<Long, Post> candidates = new TreeMap<>(Comparator.reverseOrder());
        addPushedPosts(userId, beforeId, followed, candidates);
        addPulledCelebrityPosts(followed, beforeId, limit, candidates);

        List<Post> page = new ArrayList<>(candidates.values()).subList(0, Math.min(limit, candidates.size()));
        Long nextCursor = page.size() == limit ? page.get(page.size() - 1).id() : null;
        return new FeedPage(List.copyOf(page), nextCursor);
    }

    public boolean isCelebrity(String userId) {
        return graph.followerCount(userId) >= celebrityFollowerThreshold;
    }

    private void addPushedPosts(String userId, long beforeId, Set<String> followed, NavigableMap<Long, Post> candidates) {
        for (long postId : feedCache.postIdsBefore(userId, beforeId)) {
            posts.find(postId)
                    .filter(post -> post.authorId().equals(userId) || followed.contains(post.authorId()))
                    .ifPresent(post -> candidates.put(post.id(), post));
        }
    }

    private void addPulledCelebrityPosts(Set<String> followed, long beforeId, int limit, NavigableMap<Long, Post> candidates) {
        for (String authorId : followed) {
            if (isCelebrity(authorId)) {
                posts.recentByAuthor(authorId, beforeId, limit).forEach(post -> candidates.put(post.id(), post));
            }
        }
    }
}
