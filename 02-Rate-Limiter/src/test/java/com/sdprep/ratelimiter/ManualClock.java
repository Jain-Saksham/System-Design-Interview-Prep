package com.sdprep.ratelimiter;

import java.util.function.LongSupplier;

/** A clock that only moves when the test says so. */
class ManualClock implements LongSupplier {

    private volatile long nowMillis;

    void advanceMillis(long millis) {
        nowMillis += millis;
    }

    void setMillis(long millis) {
        nowMillis = millis;
    }

    @Override
    public long getAsLong() {
        return nowMillis;
    }
}
