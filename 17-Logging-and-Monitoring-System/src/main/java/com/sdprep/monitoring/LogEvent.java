package com.sdprep.monitoring;

/** One structured log line. Structured fields make logs searchable and countable, unlike free text. */
public record LogEvent(long timestampMillis, String service, LogLevel level, String message) {

    public long minuteBucket() {
        return timestampMillis / 60_000;
    }
}
