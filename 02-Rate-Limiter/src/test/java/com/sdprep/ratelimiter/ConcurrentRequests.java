package com.sdprep.ratelimiter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Fires many requests for one client at the same instant and counts how many were allowed. */
final class ConcurrentRequests {

    private ConcurrentRequests() {
    }

    static int countAllowed(RateLimiter limiter, String clientId, int requests) throws InterruptedException {
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(requests);

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < requests; i++) {
                pool.submit(() -> {
                    try {
                        startTogether.await();
                        if (limiter.tryAcquire(clientId)) {
                            allowed.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            startTogether.countDown();
            finished.await();
        }
        return allowed.get();
    }
}
