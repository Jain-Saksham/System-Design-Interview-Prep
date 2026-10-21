package com.sdprep.monitoring;

import com.sdprep.monitoring.AlertEvaluator.Alert;
import com.sdprep.monitoring.AlertEvaluator.AlertRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertEvaluatorTest {

    private final MetricsAggregator metrics = new MetricsAggregator();
    private final List<Alert> notified = new ArrayList<>();
    private AlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        // ≥ 3 errors over the last 2 complete minutes; at most one page per 5 minutes.
        AlertRule rule = new AlertRule("api-errors", "api", LogLevel.ERROR, 3, 2, 5);
        evaluator = new AlertEvaluator(metrics, List.of(rule), notified::add);
    }

    @Test
    void firesWhenThresholdIsReachedInTheWindow() {
        errors(10, 2);
        errors(11, 1);

        assertEquals(1, evaluator.evaluate(12).size());
        assertEquals(3, notified.get(0).observedCount());
    }

    @Test
    void staysQuietBelowThresholdAndIgnoresOtherLevels() {
        errors(10, 2);
        metrics.record(new LogEvent(11 * 60_000, "api", LogLevel.WARN, "not an error"));

        assertTrue(evaluator.evaluate(12).isEmpty());
    }

    @Test
    void cooldownSuppressesRepeatPagesForTheSameIncident() {
        for (int minute = 10; minute < 20; minute++) {
            errors(minute, 5); // a long outage
        }

        int pages = 0;
        for (int minute = 12; minute <= 20; minute++) {
            pages += evaluator.evaluate(minute).size();
        }

        assertEquals(2, pages); // at minute 12 and again at 17, not 9 times
    }

    private void errors(int minute, int count) {
        for (int i = 0; i < count; i++) {
            metrics.record(new LogEvent(minute * 60_000L + i, "api", LogLevel.ERROR, "boom"));
        }
    }
}
