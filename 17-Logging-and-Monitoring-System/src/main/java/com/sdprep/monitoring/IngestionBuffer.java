package com.sdprep.monitoring;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded buffer between log producers and processing. When full, new events are dropped and counted
 * instead of blocking producers: logging must never slow down or crash the application being logged.
 *
 * <p>Production: Kafka is the buffer (durable, days of retention), and agents on each host
 * (Fluent Bit, Vector) batch and retry.
 */
public class IngestionBuffer {

    private final BlockingQueue<LogEvent> queue;
    private final AtomicLong dropped = new AtomicLong();

    public IngestionBuffer(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /** @return false if the buffer was full and the event was dropped */
    public boolean offer(LogEvent event) {
        boolean accepted = queue.offer(event);
        if (!accepted) {
            dropped.incrementAndGet(); // expose as a metric and alert on it
        }
        return accepted;
    }

    public List<LogEvent> drain(int maxEvents) {
        List<LogEvent> batch = new ArrayList<>();
        queue.drainTo(batch, maxEvents);
        return batch;
    }

    public long droppedCount() {
        return dropped.get();
    }
}
