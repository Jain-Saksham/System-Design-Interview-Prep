package com.sdprep.ratelimiter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    private final ManualClock clock = new ManualClock();

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        RateLimiter limiter = new TokenBucketRateLimiter(3, 1.0, clock);

        assertTrue(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
    }

    @Test
    void refillsAtConfiguredRate() {
        RateLimiter limiter = new TokenBucketRateLimiter(1, 2.0, clock); // 1 token every 500 ms
        assertTrue(limiter.tryAcquire("alice"));

        clock.advanceMillis(499);
        assertFalse(limiter.tryAcquire("alice"));

        clock.advanceMillis(1);
        assertTrue(limiter.tryAcquire("alice"));
    }

    @Test
    void longIdlePeriodRefillsOnlyUpToCapacity() {
        RateLimiter limiter = new TokenBucketRateLimiter(2, 1.0, clock);
        limiter.tryAcquire("alice");

        clock.advanceMillis(60_000); // would be 60 tokens without the cap

        assertEquals(2, countAllowed(limiter, "alice", 10));
    }

    @Test
    void eachClientHasItsOwnBucket() {
        RateLimiter limiter = new TokenBucketRateLimiter(1, 1.0, clock);

        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("bob"));
    }

    @Test
    void concurrentRequestsNeverExceedCapacity() throws InterruptedException {
        RateLimiter limiter = new TokenBucketRateLimiter(50, 1.0, clock); // clock frozen: no refill

        assertEquals(50, ConcurrentRequests.countAllowed(limiter, "alice", 500));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, 1.0, clock));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(1, 0, clock));
    }

    private static int countAllowed(RateLimiter limiter, String clientId, int attempts) {
        int allowed = 0;
        for (int i = 0; i < attempts; i++) {
            if (limiter.tryAcquire(clientId)) {
                allowed++;
            }
        }
        return allowed;
    }
}
