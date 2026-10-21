package com.sdprep.scheduler;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Durable job state in production (a DB table); in memory here. All state changes are synchronized,
 * so leasing is atomic: two workers can never lease the same job at the same time.
 *
 * <p>Production equivalent of {@link #leaseDueJobs}:
 * <pre>
 * SELECT * FROM jobs WHERE (status = 'SCHEDULED' AND next_run_at <= now())
 *                       OR (status = 'RUNNING'   AND lease_expires_at <= now())
 *  ORDER BY next_run_at LIMIT :n FOR UPDATE SKIP LOCKED;   -- then UPDATE to RUNNING with a lease
 * </pre>
 */
public class JobStore {

    private final Map<String, Job> jobsById = new LinkedHashMap<>();

    public synchronized void save(Job job) {
        jobsById.put(job.id(), job);
    }

    public synchronized Optional<Job> find(String jobId) {
        return Optional.ofNullable(jobsById.get(jobId));
    }

    /**
     * Claims up to {@code limit} jobs that are due. A RUNNING job whose lease has expired is also
     * claimable: its worker probably crashed, so the job runs again (at-least-once execution).
     */
    public synchronized List<Job> leaseDueJobs(String workerId, long nowMillis, long leaseMillis, int limit) {
        List<Job> due = jobsById.values().stream()
                .filter(job -> isDue(job, nowMillis))
                .sorted(Comparator.comparingLong(Job::nextRunAtMillis))
                .limit(limit)
                .toList();
        return due.stream()
                .map(job -> {
                    Job leased = job.leased(workerId, nowMillis + leaseMillis);
                    jobsById.put(job.id(), leased);
                    return leased;
                })
                .toList();
    }

    /** @return false if this worker no longer holds the lease (it was too slow; someone else took over) */
    public synchronized boolean complete(String jobId, String workerId, long nowMillis) {
        Job job = jobsById.get(jobId);
        if (!holdsLease(job, workerId)) {
            return false;
        }
        jobsById.put(jobId, job.isRecurring()
                ? job.rescheduled(nextSlotAfter(job, nowMillis), 0, null)
                : job.finished(Job.Status.SUCCEEDED, null));
        return true;
    }

    /** Retries with exponential backoff until {@code maxAttempts}, then marks the job FAILED. */
    public synchronized boolean fail(String jobId, String workerId, long nowMillis, String error, long baseBackoffMillis) {
        Job job = jobsById.get(jobId);
        if (!holdsLease(job, workerId)) {
            return false;
        }
        if (job.attempts() >= job.maxAttempts()) {
            jobsById.put(jobId, job.finished(Job.Status.FAILED, error)); // alert a human; production keeps a dead-letter list
        } else {
            long backoff = baseBackoffMillis * (1L << (job.attempts() - 1)); // 1×, 2×, 4× ...
            jobsById.put(jobId, job.rescheduled(nowMillis + backoff, job.attempts(), error));
        }
        return true;
    }

    /** Only jobs that haven't started can be cancelled. */
    public synchronized boolean cancel(String jobId) {
        Job job = jobsById.get(jobId);
        if (job == null || job.status() != Job.Status.SCHEDULED) {
            return false;
        }
        jobsById.put(jobId, job.finished(Job.Status.CANCELLED, null));
        return true;
    }

    private static boolean isDue(Job job, long nowMillis) {
        boolean scheduledAndDue = job.status() == Job.Status.SCHEDULED && job.nextRunAtMillis() <= nowMillis;
        boolean leaseExpired = job.status() == Job.Status.RUNNING && job.leaseExpiresAtMillis() <= nowMillis;
        return scheduledAndDue || leaseExpired;
    }

    private static boolean holdsLease(Job job, String workerId) {
        return job != null && job.status() == Job.Status.RUNNING && workerId.equals(job.leaseOwner());
    }

    /** Next run on the original cadence. Missed runs (e.g. after downtime) are skipped, not replayed in a burst. */
    private static long nextSlotAfter(Job job, long nowMillis) {
        long next = job.nextRunAtMillis() + job.intervalMillis();
        while (next <= nowMillis) {
            next += job.intervalMillis();
        }
        return next;
    }
}
