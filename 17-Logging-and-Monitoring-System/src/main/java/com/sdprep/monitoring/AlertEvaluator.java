package com.sdprep.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Checks alert rules against the metrics, e.g. "page if checkout logs ≥ 5 ERRORs in 2 minutes".
 * A cooldown stops one incident from sending a page every evaluation cycle.
 */
public class AlertEvaluator {

    /** @param windowMinutes how many recent complete minutes to sum */
    public record AlertRule(String name, String service, LogLevel level, long threshold,
                            int windowMinutes, int cooldownMinutes) {
    }

    public record Alert(String ruleName, String service, long observedCount, long atMinute) {
    }

    private final MetricsAggregator metrics;
    private final List<AlertRule> rules;
    private final Consumer<Alert> notifier; // production: PagerDuty, Slack, email
    private final Map<String, Long> lastFiredMinuteByRule = new HashMap<>();

    public AlertEvaluator(MetricsAggregator metrics, List<AlertRule> rules, Consumer<Alert> notifier) {
        this.metrics = metrics;
        this.rules = List.copyOf(rules);
        this.notifier = notifier;
    }

    /** Run periodically (e.g. every minute). Uses only complete minutes before {@code currentMinute}. */
    public synchronized List<Alert> evaluate(long currentMinute) {
        List<Alert> fired = new ArrayList<>();
        for (AlertRule rule : rules) {
            long count = metrics.countInWindow(rule.service(), rule.level(), currentMinute - rule.windowMinutes(), currentMinute);
            Long lastFired = lastFiredMinuteByRule.get(rule.name());
            boolean coolingDown = lastFired != null && currentMinute - lastFired < rule.cooldownMinutes();
            if (count >= rule.threshold() && !coolingDown) {
                Alert alert = new Alert(rule.name(), rule.service(), count, currentMinute);
                lastFiredMinuteByRule.put(rule.name(), currentMinute);
                notifier.accept(alert);
                fired.add(alert);
            }
        }
        return fired;
    }
}
