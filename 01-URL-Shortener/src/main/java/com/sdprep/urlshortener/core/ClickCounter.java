package com.sdprep.urlshortener.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts redirects per code.
 *
 * <p>Production: the redirect path only publishes a click event to Kafka and returns
 * immediately; a separate consumer aggregates counts. Analytics must never slow down redirects.
 */
public class ClickCounter {

    // LongAdder handles many concurrent increments better than AtomicLong.
    private final Map<String, LongAdder> clicksByCode = new ConcurrentHashMap<>();

    public void recordClick(String code) {
        clicksByCode.computeIfAbsent(code, unused -> new LongAdder()).increment();
    }

    public long clicksFor(String code) {
        LongAdder clicks = clicksByCode.get(code);
        return clicks == null ? 0 : clicks.sum();
    }
}
