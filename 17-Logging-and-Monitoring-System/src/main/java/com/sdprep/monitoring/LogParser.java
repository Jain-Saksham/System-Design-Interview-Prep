package com.sdprep.monitoring;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Parses lines of the form {@code <ISO-8601 time> <service> <LEVEL> <message...>}, e.g.
 * {@code 2026-01-15T10:02:07Z checkout ERROR payment provider timeout}.
 *
 * <p>Malformed lines are rejected (and counted by the caller) rather than crashing the pipeline:
 * one bad log line from one service must never stop ingestion for everyone.
 */
public final class LogParser {

    private LogParser() {
    }

    public static Optional<LogEvent> parse(String line) {
        String[] parts = line.trim().split("\\s+", 4);
        if (parts.length < 4) {
            return Optional.empty();
        }
        try {
            long timestamp = Instant.parse(parts[0]).toEpochMilli();
            LogLevel level = LogLevel.valueOf(parts[2]);
            return Optional.of(new LogEvent(timestamp, parts[1], level, parts[3]));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
