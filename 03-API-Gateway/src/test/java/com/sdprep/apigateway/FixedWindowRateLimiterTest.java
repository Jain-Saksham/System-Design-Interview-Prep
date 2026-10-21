package com.sdprep.apigateway;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWindowRateLimiterTest {

    private final AtomicLong clock = new AtomicLong();
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, 1_000, clock::get);

    @Test
    void allowsLimitPerWindowThenRejects() {
        assertTrue(limiter.tryAcquire("app"));
        assertTrue(limiter.tryAcquire("app"));
        assertFalse(limiter.tryAcquire("app"));
    }

    @Test
    void newWindowResetsTheCount() {
        limiter.tryAcquire("app");
        limiter.tryAcquire("app");

        clock.set(1_000);

        assertTrue(limiter.tryAcquire("app"));
    }

    @Test
    void clientsAreLimitedIndependently() {
        limiter.tryAcquire("app");
        limiter.tryAcquire("app");

        assertTrue(limiter.tryAcquire("other-app"));
    }
}
