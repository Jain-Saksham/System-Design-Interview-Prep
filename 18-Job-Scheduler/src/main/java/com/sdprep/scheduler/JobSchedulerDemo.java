package com.sdprep.scheduler;

import com.sdprep.scheduler.Worker.Execution;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Runnable walkthrough with simulated time: one-off, recurring, retries with backoff, and a crashed worker. */
public class JobSchedulerDemo {

    private static final AtomicLong clock = new AtomicLong();
    private static final long LEASE_MILLIS = 5_000;

    public static void main(String[] args) {
        JobStore store = new JobStore();
        JobScheduler scheduler = new JobScheduler(store);
        AtomicInteger reportRuns = new AtomicInteger();
        Map<String, JobHandler> handlers = Map.of(
                "send-email", job -> { },
                "cleanup", job -> { },
                "export", job -> { },
                "build-report", job -> {
                    if (reportRuns.incrementAndGet() <= 2) {
                        throw new IllegalStateException("warehouse busy");
                    }
                });
        Worker workerA = new Worker("worker-A", store, handlers, clock::get, LEASE_MILLIS, 2_000);
        Worker workerB = new Worker("worker-B", store, handlers, clock::get, LEASE_MILLIS, 2_000);

        scheduler.scheduleOnce("send-email", "welcome:u42", 5_000, 3);
        scheduler.scheduleRecurring("cleanup", "tmp/", 0, 10_000, 3);
        scheduler.scheduleOnce("build-report", "daily", 0, 4);
        Job export = scheduler.scheduleOnce("export", "orders.csv", 12_000, 3);
        System.out.println("Scheduled: send-email at 5 s, cleanup every 10 s, build-report now (flaky), export at 12 s");
        System.out.println("Lease = 5 s, retry backoff = 2 s, 4 s, ...\n");

        for (long second = 0; second <= 21; second++) {
            clock.set(second * 1_000);
            if (second == 12) {
                // worker-A claims the export job and then crashes before finishing it.
                store.leaseDueJobs("worker-A", clock.get(), LEASE_MILLIS, 1);
                System.out.println("t=12 s  worker-A leased export (" + export.id() + ") and CRASHED");
                continue;
            }
            Worker worker = second % 2 == 0 ? workerA : workerB; // two workers taking turns
            if (second >= 12 && second < 18) {
                worker = workerB; // worker-A is down
            }
            print(second, worker, worker.pollOnce(10));
        }

        boolean lateCompletion = store.complete(export.id(), "worker-A", clock.get());
        System.out.println("\nworker-A comes back and reports export done -> accepted? " + lateCompletion
                + " (its lease expired; worker-B already re-ran the job)");
        System.out.println("Final: send-email " + scheduler.get("job-1").status() + ", build-report "
                + scheduler.get("job-3").status() + ", export " + scheduler.get(export.id()).status()
                + ", cleanup next at " + scheduler.get("job-2").nextRunAtMillis() / 1_000 + " s");
    }

    private static void print(long second, Worker worker, List<Execution> executions) {
        for (Execution execution : executions) {
            System.out.printf("t=%2d s  %s ran %-12s attempt %d -> %s%n", second, worker.id(), execution.type(),
                    execution.attempt(), execution.succeeded() ? "ok" : "FAILED (" + execution.error() + "), will retry");
        }
    }
}
