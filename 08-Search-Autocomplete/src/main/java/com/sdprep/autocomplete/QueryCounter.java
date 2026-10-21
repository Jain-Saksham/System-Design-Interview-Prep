package com.sdprep.autocomplete;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts completed searches as they happen.
 *
 * <p>Production: search events go to Kafka; a batch or streaming job aggregates counts per time
 * window (often with decay so recent searches weigh more) and writes them for the next trie build.
 */
public class QueryCounter {

    private final Map<String, LongAdder> countsByQuery = new ConcurrentHashMap<>();

    public void record(String rawQuery) {
        String query = QueryNormalizer.normalize(rawQuery);
        if (!query.isEmpty()) {
            countsByQuery.computeIfAbsent(query, unused -> new LongAdder()).increment();
        }
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new HashMap<>();
        countsByQuery.forEach((query, count) -> snapshot.put(query, count.sum()));
        return snapshot;
    }
}
