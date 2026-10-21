package com.sdprep.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Sliding window counter: allows at most {@code limit} requests in any rolling window.
 *
 * <p>Keeps only two counters per client (previous and current fixed window) and estimates the
 * rolling count as: {@code previous × (share of previous window still inside the rolling window) + current}.
 * This avoids the fixed-window flaw where a client sends {@code limit} requests at the end of one
 * window and {@code limit} more at the start of the next. Used by Cloudflare.
 */
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clockMillis;
    private final Map<String, Window> windowsByClient = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(int limit, long windowMillis) {
        this(limit, windowMillis, MonotonicClock::millis);
    }

    /** @param clockMillis time source; tests pass a manual clock */
    public SlidingWindowCounterRateLimiter(int limit, long windowMillis, LongSupplier clockMillis) {
        if (limit <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clockMillis = clockMillis;
    }

    @Override
    public boolean tryAcquire(String clientId) {
        Window window = windowsByClient.computeIfAbsent(clientId, id -> new Window(clockMillis.getAsLong()));
        return window.tryRecordRequest();
    }

    private long windowStartFor(long timeMillis) {
        return timeMillis - Math.floorMod(timeMillis, windowMillis);
    }

    /** One client's counters. */
    private final class Window {
        private long currentWindowStart;
        private int currentCount;
        private int previousCount;

        Window(long nowMillis) {
            this.currentWindowStart = windowStartFor(nowMillis);
        }

        synchronized boolean tryRecordRequest() {
            long now = clockMillis.getAsLong();
            rollWindowIfNeeded(now);

            double fractionOfCurrentWindowElapsed = (now - currentWindowStart) / (double) windowMillis;
            double estimatedCount = previousCount * (1 - fractionOfCurrentWindowElapsed) + currentCount;

            if (estimatedCount < limit) {
                currentCount++;
                return true;
            }
            return false;
        }

        private void rollWindowIfNeeded(long now) {
            long windowStart = windowStartFor(now);
            if (windowStart == currentWindowStart) {
                return;
            }
            // Previous counts only matter if the last window is directly before this one.
            boolean lastWindowIsAdjacent = windowStart - currentWindowStart == windowMillis;
            previousCount = lastWindowIsAdjacent ? currentCount : 0;
            currentCount = 0;
            currentWindowStart = windowStart;
        }
    }
}
