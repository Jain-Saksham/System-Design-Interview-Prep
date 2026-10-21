package com.sdprep.monitoring;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wires the stages together: raw line → parse → buffer → (store + metrics).
 * Collection (submit) and processing (processBatch) are separate steps, as they would be separate
 * services in production, connected by Kafka.
 */
public class LogPipeline {

    private final IngestionBuffer buffer;
    private final LogStore store;
    private final MetricsAggregator metrics;
    private final AtomicLong malformedLines = new AtomicLong();

    public LogPipeline(IngestionBuffer buffer, LogStore store, MetricsAggregator metrics) {
        this.buffer = buffer;
        this.store = store;
        this.metrics = metrics;
    }

    /** Called by log agents. Never throws for bad input. */
    public void submit(String rawLine) {
        LogParser.parse(rawLine).ifPresentOrElse(buffer::offer, malformedLines::incrementAndGet);
    }

    /** Called by processing workers. Returns how many events were processed. */
    public int processBatch(int maxEvents) {
        List<LogEvent> batch = buffer.drain(maxEvents);
        for (LogEvent event : batch) {
            store.append(event);   // for search
            metrics.record(event); // for dashboards and alerts
        }
        return batch.size();
    }

    public long malformedLines() {
        return malformedLines.get();
    }

    public long droppedEvents() {
        return buffer.droppedCount();
    }
}
