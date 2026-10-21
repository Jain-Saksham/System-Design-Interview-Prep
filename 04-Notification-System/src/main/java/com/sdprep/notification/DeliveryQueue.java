package com.sdprep.notification;

import java.util.Comparator;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Tasks ordered by when they become due (first-in-first-out among equal due times).
 * Retries are re-added with a later due time.
 *
 * <p>Production: a durable queue, typically one Kafka topic (or SQS queue) per channel, plus
 * delayed retry topics. Messages survive crashes, and each channel's workers scale independently.
 */
public class DeliveryQueue {

    private record Entry(DeliveryTask task, long insertionOrder) {
    }

    private final PriorityQueue<Entry> entries = new PriorityQueue<>(
            Comparator.comparingLong((Entry entry) -> entry.task().dueAtMillis())
                    .thenComparingLong(Entry::insertionOrder));
    private long nextInsertionOrder;

    public synchronized void add(DeliveryTask task) {
        entries.add(new Entry(task, nextInsertionOrder++));
    }

    /** Removes and returns the earliest task if it is due by {@code nowMillis}. */
    public synchronized Optional<DeliveryTask> pollDue(long nowMillis) {
        Entry next = entries.peek();
        if (next == null || next.task().dueAtMillis() > nowMillis) {
            return Optional.empty();
        }
        return Optional.of(entries.poll().task());
    }

    public synchronized int size() {
        return entries.size();
    }
}
