package com.sdprep.apigateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Allows {@code limit} requests per client per fixed window. Deliberately the simplest algorithm;
 * see 02-Rate-Limiter for token bucket and sliding window, which avoid the burst at window boundaries.
 */
public class FixedWindowRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clockMillis;
    private final Map<String, Window> windowsByClient = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, long windowMillis, LongSupplier clockMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clockMillis = clockMillis;
    }

    public boolean tryAcquire(String clientId) {
        long now = clockMillis.getAsLong();
        long windowStart = now - Math.floorMod(now, windowMillis);
        // compute() runs atomically per key, so concurrent requests can't both take the last slot.
        Window window = windowsByClient.compute(clientId, (id, current) ->
                current == null || current.start() != windowStart
                        ? new Window(windowStart, 1)
                        : new Window(windowStart, current.count() + 1));
        return window.count() <= limit;
    }

    private record Window(long start, int count) {
    }
}
