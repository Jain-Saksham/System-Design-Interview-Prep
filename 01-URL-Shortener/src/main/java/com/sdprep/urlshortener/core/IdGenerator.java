package com.sdprep.urlshortener.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Hands out unique, increasing IDs within this one process.
 *
 * <p>Production: several app instances need non-overlapping IDs, so each instance leases
 * a block (e.g., 10,000 IDs) from a database sequence or ZooKeeper and serves from it in
 * memory. That keeps IDs unique without a network call per request.
 */
public class IdGenerator {

    // 62^3: the first ID encodes to "1000", so every generated code has at least 4 characters.
    private static final long FIRST_ID = 238_328L;

    private final AtomicLong nextId = new AtomicLong(FIRST_ID);

    public long nextId() {
        return nextId.getAndIncrement();
    }
}
