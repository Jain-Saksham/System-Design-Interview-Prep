package com.sdprep.ratelimiter;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runnable walkthrough of both algorithms. Uses simulated time so the output is deterministic.
 * Each request prints the HTTP status an API would return: 200 (allowed) or 429 (rejected).
 */
public class RateLimiterDemo {

    private static final AtomicLong simulatedMillis = new AtomicLong();

    public static void main(String[] args) {
        tokenBucketDemo();
        slidingWindowDemo();
    }

    private static void tokenBucketDemo() {
        System.out.println("Token bucket: capacity 5, refills 1 token/second");
        RateLimiter limiter = new TokenBucketRateLimiter(5, 1.0, simulatedMillis::get);

        sendRequests(limiter, 0, "alice", 8);    // burst: 5 allowed, then empty
        sendRequests(limiter, 0, "bob", 2);      // separate bucket, unaffected by alice
        sendRequests(limiter, 2_000, "alice", 3); // 2 seconds later: 2 tokens refilled
        System.out.println();
    }

    private static void slidingWindowDemo() {
        System.out.println("Sliding window counter: 5 requests per rolling 1000 ms");
        RateLimiter limiter = new SlidingWindowCounterRateLimiter(5, 1_000, simulatedMillis::get);

        sendRequests(limiter, 900, "carol", 5);   // end of window [0, 1000)
        sendRequests(limiter, 1_100, "carol", 5); // start of next window
        System.out.println("  A fixed-window limiter would have allowed all 10 requests within 200 ms.");
        sendRequests(limiter, 2_500, "carol", 5); // the earlier burst has mostly slid out of the window
    }

    private static void sendRequests(RateLimiter limiter, long atMillis, String clientId, int count) {
        simulatedMillis.set(atMillis);
        StringJoiner statuses = new StringJoiner(" ");
        for (int i = 0; i < count; i++) {
            statuses.add(limiter.tryAcquire(clientId) ? "200" : "429");
        }
        System.out.printf("  t=%5d ms  %-6s x%d -> %s%n", atMillis, clientId, count, statuses);
    }
}
