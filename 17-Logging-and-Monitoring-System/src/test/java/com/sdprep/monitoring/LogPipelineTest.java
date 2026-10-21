package com.sdprep.monitoring;

import com.sdprep.monitoring.LogStore.Query;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogPipelineTest {

    private static final long MINUTE = 60_000;

    @Test
    void parsesStructuredLinesAndRejectsMalformedOnes() {
        LogEvent event = LogParser.parse("2026-01-15T10:00:05Z checkout ERROR payment timeout order=9").orElseThrow();

        assertEquals("checkout", event.service());
        assertEquals(LogLevel.ERROR, event.level());
        assertEquals("payment timeout order=9", event.message());
        assertTrue(LogParser.parse("not a log line").isEmpty());
        assertTrue(LogParser.parse("2026-01-15T10:00:05Z checkout LOUD message").isEmpty());
        assertTrue(LogParser.parse("yesterday checkout ERROR message").isEmpty());
    }

    @Test
    void fullBufferDropsAndCountsInsteadOfBlocking() {
        IngestionBuffer buffer = new IngestionBuffer(2);
        LogEvent event = new LogEvent(0, "s", LogLevel.INFO, "m");

        assertTrue(buffer.offer(event));
        assertTrue(buffer.offer(event));
        assertFalse(buffer.offer(event));
        assertEquals(1, buffer.droppedCount());
    }

    @Test
    void pipelineFeedsBothSearchAndMetrics() {
        LogStore store = new LogStore();
        MetricsAggregator metrics = new MetricsAggregator();
        LogPipeline pipeline = new LogPipeline(new IngestionBuffer(100), store, metrics);

        pipeline.submit("1970-01-01T00:00:10Z api ERROR boom");
        pipeline.submit("1970-01-01T00:00:20Z api ERROR boom again");
        pipeline.submit("1970-01-01T00:01:05Z api INFO fine");
        pipeline.submit("junk");
        assertEquals(3, pipeline.processBatch(100));

        assertEquals(2, metrics.count("api", LogLevel.ERROR, 0));
        assertEquals(1, metrics.count("api", LogLevel.INFO, 1));
        assertEquals(1, pipeline.malformedLines());
        assertEquals(3, store.search(new Query(null, null, 0, 2 * MINUTE, null, 10)).size());
    }

    @Test
    void searchFiltersByServiceLevelTimeAndTextNewestFirst() {
        LogStore store = new LogStore();
        store.append(new LogEvent(1_000, "api", LogLevel.INFO, "request ok"));
        store.append(new LogEvent(2_000, "api", LogLevel.ERROR, "db timeout"));
        store.append(new LogEvent(3_000, "api", LogLevel.WARN, "slow timeout"));
        store.append(new LogEvent(4_000, "web", LogLevel.ERROR, "timeout"));
        store.append(new LogEvent(5 * MINUTE, "api", LogLevel.ERROR, "late timeout"));

        List<LogEvent> results = store.search(new Query("api", LogLevel.WARN, 0, MINUTE, "timeout", 10));

        assertEquals(List.of("slow timeout", "db timeout"), results.stream().map(LogEvent::message).toList());
    }

    @Test
    void retentionDropsWholeOldPartitions() {
        LogStore store = new LogStore();
        store.append(new LogEvent(0, "api", LogLevel.INFO, "old"));
        store.append(new LogEvent(10 * MINUTE, "api", LogLevel.INFO, "new"));

        assertEquals(1, store.deleteOlderThan(5 * MINUTE));
        assertEquals(1, store.search(new Query(null, null, 0, 20 * MINUTE, null, 10)).size());
    }
}
