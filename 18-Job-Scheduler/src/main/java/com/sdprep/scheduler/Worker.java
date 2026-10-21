package com.sdprep.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Polls the store for due jobs, runs them, and reports the outcome. Run many of these for scale. */
public class Worker {

    /** What happened to one job during a poll. */
    public record Execution(String jobId, String type, int attempt, boolean succeeded, String error) {
    }

    private final String workerId;
    private final JobStore store;
    private final Map<String, JobHandler> handlersByType;
    private final LongSupplier clockMillis;
    private final long leaseMillis;
    private final long baseBackoffMillis;

    public Worker(String workerId, JobStore store, Map<String, JobHandler> handlersByType,
                  LongSupplier clockMillis, long leaseMillis, long baseBackoffMillis) {
        this.workerId = workerId;
        this.store = store;
        this.handlersByType = Map.copyOf(handlersByType);
        this.clockMillis = clockMillis;
        this.leaseMillis = leaseMillis;
        this.baseBackoffMillis = baseBackoffMillis;
    }

    public String id() {
        return workerId;
    }

    public List<Execution> pollOnce(int maxJobs) {
        List<Execution> executions = new ArrayList<>();
        for (Job job : store.leaseDueJobs(workerId, clockMillis.getAsLong(), leaseMillis, maxJobs)) {
            executions.add(execute(job));
        }
        return executions;
    }

    private Execution execute(Job job) {
        JobHandler handler = handlersByType.get(job.type());
        try {
            if (handler == null) {
                throw new IllegalStateException("No handler for job type " + job.type());
            }
            handler.run(job);
            store.complete(job.id(), workerId, clockMillis.getAsLong());
            return new Execution(job.id(), job.type(), job.attempts(), true, null);
        } catch (Exception e) {
            store.fail(job.id(), workerId, clockMillis.getAsLong(), e.getMessage(), baseBackoffMillis);
            return new Execution(job.id(), job.type(), job.attempts(), false, e.getMessage());
        }
    }
}
