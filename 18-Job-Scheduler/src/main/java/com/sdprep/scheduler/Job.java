package com.sdprep.scheduler;

/**
 * A unit of scheduled work. Immutable: the store replaces it on every state change.
 *
 * @param type           selects the handler, e.g. "send-email"
 * @param intervalMillis 0 for a one-off job, otherwise the period of a recurring job
 * @param attempts       how many times it has been started in the current run (reset after success)
 * @param leaseOwner     the worker currently running it; the lease expires at {@code leaseExpiresAtMillis}
 */
public record Job(String id, String type, String payload, long nextRunAtMillis, long intervalMillis,
                  int attempts, int maxAttempts, Status status, String leaseOwner, long leaseExpiresAtMillis,
                  String lastError) {

    public enum Status { SCHEDULED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    static Job scheduled(String id, String type, String payload, long runAtMillis, long intervalMillis, int maxAttempts) {
        return new Job(id, type, payload, runAtMillis, intervalMillis, 0, maxAttempts, Status.SCHEDULED, null, 0, null);
    }

    public boolean isRecurring() {
        return intervalMillis > 0;
    }

    Job leased(String workerId, long leaseExpiresAt) {
        return new Job(id, type, payload, nextRunAtMillis, intervalMillis, attempts + 1, maxAttempts,
                Status.RUNNING, workerId, leaseExpiresAt, lastError);
    }

    Job rescheduled(long runAtMillis, int attemptsSoFar, String error) {
        return new Job(id, type, payload, runAtMillis, intervalMillis, attemptsSoFar, maxAttempts,
                Status.SCHEDULED, null, 0, error);
    }

    Job finished(Status finalStatus, String error) {
        return new Job(id, type, payload, nextRunAtMillis, intervalMillis, attempts, maxAttempts,
                finalStatus, null, 0, error);
    }
}
