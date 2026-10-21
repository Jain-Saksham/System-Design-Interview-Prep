package com.sdprep.newsfeed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedServiceTest {

    private static final int CELEBRITY_THRESHOLD = 3;

    private FeedCache cache;
    private FeedService feeds;

    @BeforeEach
    void setUp() {
        cache = new FeedCache(100);
        feeds = new FeedService(new PostStore(), new FollowGraph(), cache, CELEBRITY_THRESHOLD, () -> 0L);
    }

    @Test
    void regularUsersPostsArePushedToFollowers() {
        feeds.follow("ana", "bob");
        feeds.follow("cara", "bob");
        long writesBefore = cache.totalWrites();

        feeds.publish("bob", "hello");

        assertEquals(3, cache.totalWrites() - writesBefore); // bob + 2 followers
        assertEquals(List.of("hello"), texts(feeds.getFeed("ana", 10, null)));
    }

    @Test
    void celebrityPostsAreNotPushedButAppearByPull() {
        makeCelebrity("taylor");
        long writesBefore = cache.totalWrites();

        feeds.publish("taylor", "big news");

        assertEquals(1, cache.totalWrites() - writesBefore); // own feed only
        assertEquals(List.of("big news"), texts(feeds.getFeed("fan0", 10, null)));
    }

    @Test
    void feedMergesPushedAndPulledPostsNewestFirst() {
        makeCelebrity("taylor");
        feeds.follow("fan0", "bob");

        feeds.publish("bob", "one");
        feeds.publish("taylor", "two");
        feeds.publish("bob", "three");

        assertEquals(List.of("three", "two", "one"), texts(feeds.getFeed("fan0", 10, null)));
    }

    @Test
    void paginationReturnsEveryPostExactlyOnce() {
        makeCelebrity("taylor");
        feeds.follow("fan0", "bob");
        for (int i = 1; i <= 7; i++) {
            feeds.publish(i % 2 == 0 ? "taylor" : "bob", "post " + i);
        }

        List<String> allSeen = new ArrayList<>();
        FeedPage page = feeds.getFeed("fan0", 3, null);
        allSeen.addAll(texts(page));
        while (page.nextCursor() != null) {
            page = feeds.getFeed("fan0", 3, page.nextCursor());
            allSeen.addAll(texts(page));
        }

        assertEquals(List.of("post 7", "post 6", "post 5", "post 4", "post 3", "post 2", "post 1"), allSeen);
    }

    @Test
    void lastPageHasNoCursor() {
        feeds.follow("ana", "bob");
        feeds.publish("bob", "only post");

        assertNull(feeds.getFeed("ana", 5, null).nextCursor());
    }

    @Test
    void unfollowedAuthorsDisappearFromFeed() {
        feeds.follow("ana", "bob");
        feeds.publish("bob", "hello");

        feeds.unfollow("ana", "bob");

        assertTrue(feeds.getFeed("ana", 10, null).posts().isEmpty());
    }

    @Test
    void followingBackfillsRecentPosts() {
        feeds.publish("bob", "posted before ana followed");

        feeds.follow("ana", "bob");

        assertEquals(List.of("posted before ana followed"), texts(feeds.getFeed("ana", 10, null)));
    }

    @Test
    void cacheKeepsOnlyNewestEntries() {
        FeedCache small = new FeedCache(2);
        small.add("ana", 1);
        small.add("ana", 2);
        small.add("ana", 3);

        assertEquals(List.of(3L, 2L), small.postIdsBefore("ana", Long.MAX_VALUE));
    }

    private void makeCelebrity(String userId) {
        for (int i = 0; i < CELEBRITY_THRESHOLD; i++) {
            feeds.follow("fan" + i, userId);
        }
    }

    private static List<String> texts(FeedPage page) {
        return page.posts().stream().map(Post::text).toList();
    }
}
