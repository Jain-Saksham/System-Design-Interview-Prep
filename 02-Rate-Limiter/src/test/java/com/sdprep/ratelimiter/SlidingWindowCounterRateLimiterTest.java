package com.sdprep.ratelimiter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowCounterRateLimiterTest {

    private final ManualClock clock = new ManualClock();

    @Test
    void allowsUpToLimitWithinOneWindow() {
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(3, 1_000, clock);

        assertEquals(3, countAllowed(limiter, "alice", 5));
    }

    @Test
    void preventsDoubleBurstAcrossWindowBoundary() {
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(10, 1_000, clock);
        clock.setMillis(900);
        assertEquals(10, countAllowed(limiter, "alice", 10));

        // 100 ms into the next window, 90% of the previous window still counts: 10 × 0.9 = 9.
        clock.setMillis(1_100);
        assertEquals(1, countAllowed(limiter, "alice", 10));
    }

    @Test
    void previousWindowWeightShrinksAsTimePasses() {
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(10, 1_000, clock);
        assertEquals(10, countAllowed(limiter, "alice", 10));

        // Halfway through the next window, the previous window contributes 10 × 0.5 = 5.
        clock.setMillis(1_500);
        assertEquals(5, countAllowed(limiter, "alice", 10));
    }

    @Test
    void oldWindowsAreForgottenAfterAGap() {
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(2, 1_000, clock);
        countAllowed(limiter, "alice", 2);

        clock.setMillis(5_000); // several windows later: nothing carried over

        assertEquals(2, countAllowed(limiter, "alice", 5));
    }

    @Test
    void eachClientIsCountedSeparately() {
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(1, 1_000, clock);

        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("bob"));
    }

    @Test
    void concurrentRequestsNeverExceedLimit() throws InterruptedException {
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(50, 1_000, clock);

        assertEquals(50, ConcurrentRequests.countAllowed(limiter, "alice", 500));
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
