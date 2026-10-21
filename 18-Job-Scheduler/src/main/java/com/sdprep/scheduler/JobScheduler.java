package com.sdprep.scheduler;

import java.util.concurrent.atomic.AtomicLong;

/** The client-facing API: schedule, cancel, inspect. Workers do the running. */
public class JobScheduler {

    private final JobStore store;
    private final AtomicLong nextId = new AtomicLong(1);

    public JobScheduler(JobStore store) {
        this.store = store;
    }

    public Job scheduleOnce(String type, String payload, long runAtMillis, int maxAttempts) {
        return save(type, payload, runAtMillis, 0, maxAttempts);
    }

    public Job scheduleRecurring(String type, String payload, long firstRunAtMillis, long intervalMillis, int maxAttempts) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("interval must be positive");
        }
        return save(type, payload, firstRunAtMillis, intervalMillis, maxAttempts);
    }

    public boolean cancel(String jobId) {
        return store.cancel(jobId);
    }

    public Job get(String jobId) {
        return store.find(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));
    }

    private Job save(String type, String payload, long runAtMillis, long intervalMillis, int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        Job job = Job.scheduled("job-" + nextId.getAndIncrement(), type, payload, runAtMillis, intervalMillis, maxAttempts);
        store.save(job);
        return job;
    }
}
