package com.sdprep.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Token bucket: each client has a bucket holding up to {@code capacity} tokens.
 * Tokens refill at a steady rate; each request spends one token.
 *
 * <p>Allows short bursts (up to capacity) while enforcing a long-term average rate.
 * Used by AWS API Gateway and Stripe.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final double refillTokensPerMilli;
    private final LongSupplier clockMillis;
    private final Map<String, Bucket> bucketsByClient = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillTokensPerSecond) {
        this(capacity, refillTokensPerSecond, MonotonicClock::millis);
    }

    /** @param clockMillis time source; tests pass a manual clock */
    public TokenBucketRateLimiter(int capacity, double refillTokensPerSecond, LongSupplier clockMillis) {
        if (capacity <= 0 || refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refill rate must be positive");
        }
        this.capacity = capacity;
        this.refillTokensPerMilli = refillTokensPerSecond / 1000.0;
        this.clockMillis = clockMillis;
    }

    @Override
    public boolean tryAcquire(String clientId) {
        Bucket bucket = bucketsByClient.computeIfAbsent(clientId, id -> new Bucket(clockMillis.getAsLong()));
        return bucket.tryTakeToken();
    }

    /** One client's bucket. Locking per bucket means different clients never block each other. */
    private final class Bucket {
        private double tokens = capacity; // new clients start with a full bucket
        private long lastRefillMillis;

        Bucket(long nowMillis) {
            this.lastRefillMillis = nowMillis;
        }

        synchronized boolean tryTakeToken() {
            refill(clockMillis.getAsLong()); // read the clock inside the lock so time never goes backwards
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        // Lazy refill: instead of a background timer, add the tokens earned since the last request.
        private void refill(long nowMillis) {
            long elapsedMillis = nowMillis - lastRefillMillis;
            tokens = Math.min(capacity, tokens + elapsedMillis * refillTokensPerMilli);
            lastRefillMillis = nowMillis;
        }
    }
}
