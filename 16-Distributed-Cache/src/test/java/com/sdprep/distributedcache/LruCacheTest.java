package com.sdprep.distributedcache;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LruCacheTest {

    private final AtomicLong clock = new AtomicLong();
    private final LruCache<String, Integer> cache = new LruCache<>(2, clock::get);

    @Test
    void evictsLeastRecentlyUsedWhenFull() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        assertTrue(cache.get("a").isEmpty());
        assertEquals(Optional.of(2), cache.get("b"));
        assertEquals(Optional.of(3), cache.get("c"));
        assertEquals(1, cache.evictions());
    }

    @Test
    void readingAnEntryMakesItRecentlyUsed() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");

        cache.put("c", 3); // evicts b, not a

        assertTrue(cache.get("b").isEmpty());
        assertEquals(Optional.of(1), cache.get("a"));
    }

    @Test
    void overwritingUpdatesValueAndRecency() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("a", 10);

        cache.put("c", 3); // evicts b

        assertEquals(Optional.of(10), cache.get("a"));
        assertEquals(2, cache.size());
    }

    @Test
    void entriesExpireAfterTtl() {
        cache.put("token", 1, 500);

        clock.set(499);
        assertEquals(Optional.of(1), cache.get("token"));
        clock.set(500);
        assertTrue(cache.get("token").isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    void removeDeletesEntry() {
        cache.put("a", 1);

        assertTrue(cache.remove("a"));
        assertTrue(cache.get("a").isEmpty());
    }
}
