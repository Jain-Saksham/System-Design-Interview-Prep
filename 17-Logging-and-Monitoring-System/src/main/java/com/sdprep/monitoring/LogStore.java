package com.sdprep.monitoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Searchable log storage, partitioned by minute. A time-range query only scans the minutes it covers
 * (partition pruning), the same idea as daily indices in Elasticsearch or time partitions in ClickHouse.
 *
 * <p>Production also adds an inverted index for full-text search and retention: delete whole old
 * partitions (cheap) instead of individual rows.
 */
public class LogStore {

    /** Filters for a search; null means "any". */
    public record Query(String service, LogLevel minimumLevel, long fromMillis, long toMillis, String textContains, int limit) {
    }

    private final NavigableMap<Long, List<LogEvent>> eventsByMinute = new TreeMap<>();

    public synchronized void append(LogEvent event) {
        eventsByMinute.computeIfAbsent(event.minuteBucket(), minute -> new ArrayList<>()).add(event);
    }

    /** Newest first. */
    public synchronized List<LogEvent> search(Query query) {
        long fromMinute = query.fromMillis() / 60_000;
        long toMinute = query.toMillis() / 60_000;
        return eventsByMinute.subMap(fromMinute, true, toMinute, true).values().stream() // only relevant minutes
                .flatMap(List::stream)
                .filter(event -> event.timestampMillis() >= query.fromMillis() && event.timestampMillis() < query.toMillis())
                .filter(event -> query.service() == null || event.service().equals(query.service()))
                .filter(event -> query.minimumLevel() == null || event.level().compareTo(query.minimumLevel()) >= 0)
                .filter(event -> query.textContains() == null || event.message().contains(query.textContains()))
                .sorted(Comparator.comparingLong(LogEvent::timestampMillis).reversed())
                .limit(query.limit())
                .toList();
    }

    /** Retention: drop every partition older than the cutoff. Returns the number of events removed. */
    public synchronized int deleteOlderThan(long cutoffMillis) {
        NavigableMap<Long, List<LogEvent>> old = eventsByMinute.headMap(cutoffMillis / 60_000, false);
        int removed = old.values().stream().mapToInt(List::size).sum();
        old.clear();
        return removed;
    }
}
