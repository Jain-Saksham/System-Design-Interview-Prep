package com.sdprep.monitoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Turns the log stream into time-series counters: log lines per (service, level, minute).
 * Dashboards and alerts read these small numbers instead of scanning raw logs.
 *
 * <p>Production: a time-series database (Prometheus, InfluxDB) with downsampling of old data.
 */
public class MetricsAggregator {

    private record SeriesKey(String service, LogLevel level, long minute) {
    }

    private final Map<SeriesKey, LongAdder> counts = new ConcurrentHashMap<>();

    public void record(LogEvent event) {
        counts.computeIfAbsent(new SeriesKey(event.service(), event.level(), event.minuteBucket()),
                key -> new LongAdder()).increment();
    }

    public long count(String service, LogLevel level, long minute) {
        LongAdder adder = counts.get(new SeriesKey(service, level, minute));
        return adder == null ? 0 : adder.sum();
    }

    /** Sum over minutes [fromMinute, toMinute). */
    public long countInWindow(String service, LogLevel level, long fromMinute, long toMinute) {
        long total = 0;
        for (long minute = fromMinute; minute < toMinute; minute++) {
            total += count(service, level, minute);
        }
        return total;
    }
}
