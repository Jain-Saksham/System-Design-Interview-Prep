package com.sdprep.monitoring;

/** Ordered by severity, so "WARN and above" is {@code level.compareTo(WARN) >= 0}. */
public enum LogLevel {
    DEBUG, INFO, WARN, ERROR
}
