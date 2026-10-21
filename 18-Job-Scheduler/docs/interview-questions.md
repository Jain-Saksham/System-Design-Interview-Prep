# Job Scheduler: Interview Questions

**1. Estimate the throughput.**
Assume 10M jobs/day ≈ **115 starts/s** average. Cron-style jobs cluster at round times, so peaks at :00 can be 10–50× higher. Size the system for the peak, or smooth it with jitter.

**2. How do you stop two workers from running the same job?**
Lease atomically: in one transaction, select due jobs with `FOR UPDATE SKIP LOCKED` and mark them RUNNING with `lease_owner` and `lease_expires_at`. Other workers skip the locked rows and never see them as due while the lease is valid.

**3. What happens if a worker crashes mid-job?**
Its lease expires. The poller treats RUNNING jobs with expired leases as due, so another worker picks it up. The job therefore runs at least once, possibly twice, and handlers must be idempotent.

**4. Can you guarantee exactly-once execution?**
Not end to end: a worker can finish the side effect (send the email) and crash before recording success. Provide at-least-once, plus idempotency: dedupe keys in downstream systems, "already sent?" checks, or transactional outbox writes.

**5. Why a lease instead of a lock?**
A lock held by a dead process blocks forever (or until someone notices). A lease has an expiry, so the system heals itself. Long-running jobs extend their lease with heartbeats.

**6. A worker was slow, its lease expired, and another worker re-ran the job. Now the slow one finishes. What happens?**
Its completion is rejected because it no longer owns the lease (`WHERE lease_owner = me`). The newer run's result stands. This "fencing" prevents stale workers from corrupting state.

**7. How do recurring jobs work?**
After a run completes, compute the next run time from the schedule (interval or cron, in the job's time zone) and set the job back to SCHEDULED. If runs were missed during downtime, usually skip to the next future slot rather than replaying every missed run.

**8. How do retries work?**
On failure: if attempts < max, reschedule at `now + base × 2^(attempt−1)` (with jitter and a cap). Otherwise mark FAILED, record the error, and alert. Non-retryable errors (bad payload) should fail immediately.

**9. Polling the database every second from 100 workers: is that a problem?**
It can be. Mitigations: an index on `(status, next_run_at)`, small batch leases, jittered poll intervals, or a dispatcher pattern where a few leaders push due jobs into a queue (Kafka/SQS) and workers consume from it.

**10. How do you handle the midnight spike?**
Spread load with schedule jitter (run "around" 00:00), pre-enqueue jobs due in the next few seconds, autoscale workers ahead of known peaks, and prioritize latency-sensitive jobs over batch jobs.

**11. Why use the database's clock instead of each worker's?**
Worker clocks drift. If one is 30 s fast, it could run jobs early or steal leases prematurely. Using the DB's `now()` in the lease query gives one authoritative clock.

**12. How would you support delayed messages ("run in 2 hours") at very high volume?**
Time-bucketed storage: put jobs into per-minute buckets (a Redis sorted set by timestamp, or DB partitions). A dispatcher pops each bucket when it's due and enqueues the jobs. Timing wheels give the same idea in memory.

**Likely follow-ups:** job dependencies and DAGs (Airflow) · priorities and fairness between tenants · cron parsing and DST edge cases · observability (lag between scheduled and actual start time).
