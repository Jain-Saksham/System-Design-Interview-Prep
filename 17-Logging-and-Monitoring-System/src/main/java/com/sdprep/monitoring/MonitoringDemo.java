package com.sdprep.monitoring;

import com.sdprep.monitoring.AlertEvaluator.AlertRule;
import com.sdprep.monitoring.LogStore.Query;

import java.time.Instant;
import java.util.List;

/** Runnable walkthrough: ingest logs, an error burst triggers one alert (cooldown), search, backpressure. */
public class MonitoringDemo {

    private static final long START = Instant.parse("2026-01-15T10:00:00Z").toEpochMilli();

    public static void main(String[] args) {
        LogStore store = new LogStore();
        MetricsAggregator metrics = new MetricsAggregator();
        LogPipeline pipeline = new LogPipeline(new IngestionBuffer(10_000), store, metrics);
        AlertEvaluator alerts = new AlertEvaluator(metrics,
                List.of(new AlertRule("checkout-errors", "checkout", LogLevel.ERROR, 5, 2, 10)),
                alert -> System.out.printf("  %s  ALERT %s: %d errors in the last 2 min -> page on-call%n",
                        minuteLabel(alert.atMinute()), alert.ruleName(), alert.observedCount()));

        // Minute 0-1: healthy traffic. Minutes 2-3: payment provider starts failing.
        for (int second = 0; second < 240; second += 10) {
            pipeline.submit(line(second, "checkout", "INFO", "order placed"));
            pipeline.submit(line(second + 1, "search", "INFO", "query served"));
            if (second >= 120) {
                pipeline.submit(line(second + 2, "checkout", "ERROR", "payment provider timeout order=" + second));
            }
        }
        pipeline.submit("garbage line without structure");
        pipeline.processBatch(10_000);
        System.out.println("Ingested 4 minutes of logs from checkout and search (malformed lines skipped: "
                + pipeline.malformedLines() + ")");

        System.out.println("\ncheckout ERRORs per minute:");
        for (long minute = minute(0); minute < minute(4); minute++) {
            System.out.printf("  %s  %d%n", minuteLabel(minute), metrics.count("checkout", LogLevel.ERROR, minute));
        }

        System.out.println("\nAlert evaluation, once per minute:");
        for (long minute = minute(1); minute <= minute(5); minute++) {
            if (alerts.evaluate(minute).isEmpty()) {
                long recentErrors = metrics.countInWindow("checkout", LogLevel.ERROR, minute - 2, minute);
                System.out.println("  " + minuteLabel(minute) + "  " + (recentErrors >= 5
                        ? recentErrors + " errors, but suppressed by the 10-min cooldown (same incident)"
                        : "no alert (" + recentErrors + " errors)"));
            }
        }

        System.out.println("\nSearch: checkout, level ≥ WARN, text \"timeout\", newest 3:");
        store.search(new Query("checkout", LogLevel.WARN, START, START + 240_000, "timeout", 3))
                .forEach(event -> System.out.printf("  %s %s %s%n", Instant.ofEpochMilli(event.timestampMillis()),
                        event.level(), event.message()));

        IngestionBuffer tinyBuffer = new IngestionBuffer(100);
        LogPipeline overloaded = new LogPipeline(tinyBuffer, new LogStore(), new MetricsAggregator());
        for (int i = 0; i < 150; i++) {
            overloaded.submit(line(0, "noisy", "DEBUG", "spam " + i));
        }
        System.out.println("\nBackpressure: 150 lines into a 100-slot buffer -> dropped " + overloaded.droppedEvents()
                + " (counted, never blocking the app)");
    }

    private static String line(int secondsAfterStart, String service, String level, String message) {
        return Instant.ofEpochMilli(START + secondsAfterStart * 1_000L) + " " + service + " " + level + " " + message;
    }

    private static long minute(int minutesAfterStart) {
        return START / 60_000 + minutesAfterStart;
    }

    private static String minuteLabel(long minute) {
        return Instant.ofEpochMilli(minute * 60_000).toString().substring(11, 16);
    }
}
