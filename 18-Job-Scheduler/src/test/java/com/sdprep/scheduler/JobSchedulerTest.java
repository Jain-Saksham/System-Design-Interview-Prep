package com.sdprep.scheduler;

import com.sdprep.scheduler.Worker.Execution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSchedulerTest {

    private static final long LEASE = 5_000;
    private static final long BACKOFF = 1_000;

    private final AtomicLong clock = new AtomicLong();
    private final List<String> ran = new ArrayList<>();
    private JobStore store;
    private JobScheduler scheduler;
    private Worker worker;

    @BeforeEach
    void setUp() {
        store = new JobStore();
        scheduler = new JobScheduler(store);
        worker = worker("w1", Map.of("work", job -> ran.add(job.payload())));
    }

    @Test
    void jobDoesNotRunBeforeItIsDue() {
        scheduler.scheduleOnce("work", "a", 10_000, 3);

        clock.set(9_999);
        assertTrue(worker.pollOnce(10).isEmpty());
        clock.set(10_000);
        assertEquals(1, worker.pollOnce(10).size());
    }

    @Test
    void oneOffJobRunsExactlyOnce() {
        Job job = scheduler.scheduleOnce("work", "a", 0, 3);

        worker.pollOnce(10);
        clock.set(60_000);
        worker.pollOnce(10);

        assertEquals(List.of("a"), ran);
        assertEquals(Job.Status.SUCCEEDED, scheduler.get(job.id()).status());
    }

    @Test
    void recurringJobRunsEveryInterval() {
        scheduler.scheduleRecurring("work", "tick", 0, 10_000, 3);

        for (long t = 0; t <= 30_000; t += 1_000) {
            clock.set(t);
            worker.pollOnce(10);
        }

        assertEquals(4, ran.size()); // at 0, 10, 20, 30 s
    }

    @Test
    void missedRecurringRunsAreSkippedNotReplayed() {
        Job job = scheduler.scheduleRecurring("work", "tick", 0, 10_000, 3);

        clock.set(55_000); // scheduler was down for 55 s
        worker.pollOnce(10);

        assertEquals(1, ran.size());
        assertEquals(60_000, scheduler.get(job.id()).nextRunAtMillis());
    }

    @Test
    void concurrentWorkersNeverLeaseTheSameJob() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            scheduler.scheduleOnce("work", "job" + i, 0, 3);
        }
        Set<String> leasedIds = ConcurrentHashMap.newKeySet();
        AtomicLong totalLeases = new AtomicLong();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int w = 0; w < 8; w++) {
                String workerId = "w" + w;
                pool.submit(() -> {
                    List<Job> leased;
                    while (!(leased = store.leaseDueJobs(workerId, 0, LEASE, 5)).isEmpty()) {
                        leased.forEach(job -> leasedIds.add(job.id()));
                        totalLeases.addAndGet(leased.size());
                    }
                });
            }
        }

        assertEquals(200, leasedIds.size());
        assertEquals(200, totalLeases.get()); // no job leased twice
    }

    @Test
    void crashedWorkersJobIsRetakenAfterLeaseExpires() {
        Job job = scheduler.scheduleOnce("work", "export", 0, 3);
        store.leaseDueJobs("crashed-worker", 0, LEASE, 10); // leased, never completed

        clock.set(LEASE - 1);
        assertTrue(worker.pollOnce(10).isEmpty()); // lease still valid
        clock.set(LEASE);
        assertEquals(1, worker.pollOnce(10).size());

        assertEquals(Job.Status.SUCCEEDED, scheduler.get(job.id()).status());
        assertFalse(store.complete(job.id(), "crashed-worker", LEASE + 1)); // stale worker can't overwrite
    }

    @Test
    void failingJobRetriesWithBackoffThenFails() {
        Worker failing = worker("w2", Map.of("work", job -> {
            throw new IllegalStateException("boom");
        }));
        Job job = scheduler.scheduleOnce("work", "x", 0, 3);

        List<Long> attemptTimes = new ArrayList<>();
        for (long t = 0; t <= 10_000; t += 500) {
            clock.set(t);
            for (Execution execution : failing.pollOnce(10)) {
                attemptTimes.add(t);
                assertFalse(execution.succeeded());
            }
        }

        assertEquals(List.of(0L, 1_000L, 3_000L), attemptTimes); // backoff 1 s, then 2 s
        Job finalState = scheduler.get(job.id());
        assertEquals(Job.Status.FAILED, finalState.status());
        assertEquals("boom", finalState.lastError());
    }

    @Test
    void cancelledJobNeverRuns() {
        Job job = scheduler.scheduleOnce("work", "a", 1_000, 3);

        assertTrue(scheduler.cancel(job.id()));
        clock.set(5_000);

        assertTrue(worker.pollOnce(10).isEmpty());
        assertEquals(Job.Status.CANCELLED, scheduler.get(job.id()).status());
    }

    @Test
    void unknownJobTypeFailsInsteadOfCrashingTheWorker() {
        scheduler.scheduleOnce("mystery", "a", 0, 1);

        Execution execution = worker.pollOnce(10).get(0);

        assertFalse(execution.succeeded());
        assertTrue(execution.error().contains("No handler"));
    }

    private Worker worker(String id, Map<String, JobHandler> handlers) {
        return new Worker(id, store, handlers, clock::get, LEASE, BACKOFF);
    }
}
