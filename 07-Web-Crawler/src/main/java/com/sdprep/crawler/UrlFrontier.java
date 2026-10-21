package com.sdprep.crawler;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

/**
 * The to-do list of URLs, with politeness built in: one FIFO queue per host, and a host is only
 * eligible again {@code politenessDelayMillis} after its last fetch. While one host waits, other
 * hosts' URLs are served, so the crawler stays busy without hammering any single site.
 */
public class UrlFrontier {

    public record Entry(URI url, int depth) {
    }

    private final long politenessDelayMillis;
    private final Map<String, Queue<Entry>> queuesByHost = new LinkedHashMap<>(); // insertion order = fair rotation
    private final Map<String, Long> nextAllowedFetchByHost = new LinkedHashMap<>();

    public UrlFrontier(long politenessDelayMillis) {
        this.politenessDelayMillis = politenessDelayMillis;
    }

    public void add(URI url, int depth) {
        queuesByHost.computeIfAbsent(url.getHost(), host -> new ArrayDeque<>()).add(new Entry(url, depth));
    }

    /** The next URL whose host may be contacted at {@code nowMillis}, if any. */
    public Optional<Entry> next(long nowMillis) {
        for (Map.Entry<String, Queue<Entry>> hostQueue : queuesByHost.entrySet()) {
            String host = hostQueue.getKey();
            if (!hostQueue.getValue().isEmpty() && nextAllowedFetchByHost.getOrDefault(host, 0L) <= nowMillis) {
                nextAllowedFetchByHost.put(host, nowMillis + politenessDelayMillis);
                moveToBack(host); // rotate so other hosts get the next turn
                return Optional.of(hostQueue.getValue().poll());
            }
        }
        return Optional.empty();
    }

    /** When the soonest waiting host becomes eligible. Only meaningful if not empty. */
    public long nextReadyTime() {
        return queuesByHost.entrySet().stream()
                .filter(hostQueue -> !hostQueue.getValue().isEmpty())
                .mapToLong(hostQueue -> nextAllowedFetchByHost.getOrDefault(hostQueue.getKey(), 0L))
                .min()
                .orElse(Long.MAX_VALUE);
    }

    public boolean isEmpty() {
        return queuesByHost.values().stream().allMatch(Queue::isEmpty);
    }

    private void moveToBack(String host) {
        queuesByHost.put(host, queuesByHost.remove(host));
    }
}
