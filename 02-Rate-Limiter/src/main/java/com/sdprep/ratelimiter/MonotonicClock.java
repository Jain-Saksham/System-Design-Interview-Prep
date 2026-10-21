package com.sdprep.ratelimiter;

/**
 * Milliseconds from {@link System#nanoTime()}, which never jumps backwards.
 * {@code System.currentTimeMillis()} can jump when the OS clock is adjusted (NTP),
 * which would corrupt elapsed-time calculations.
 */
final class MonotonicClock {

    private MonotonicClock() {
    }

    static long millis() {
        return System.nanoTime() / 1_000_000;
    }
}
