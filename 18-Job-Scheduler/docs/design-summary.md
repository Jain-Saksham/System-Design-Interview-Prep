# Job Scheduler: Design Summary

## Problem Statement
Applications constantly need work done later or on a schedule: reminders, report generation, cleanups, retries of failed operations. A scheduler stores those jobs durably and has a pool of workers run each one at the right time, even when workers crash, scale up or down, or the job itself fails.

## Functional Requirements
1. Schedule a one-off job at a time, or a recurring job (interval or cron).
2. Cancel a scheduled job; query its status.
3. Run due jobs on a pool of workers.
4. Retry failed jobs with backoff; give up after N attempts and surface the failure.

## Non-Functional Requirements
- **Reliability:** an accepted job is never lost; it runs at least once.
- **No concurrent double execution** while a worker is healthy.
- **Timeliness:** jobs start within a few seconds of their scheduled time.
- **Scalability:** add workers to increase throughput; millions of scheduled jobs.
- **Scale (assumed):** 10M jobs/day ≈ 115 job starts/s average, with spikes at round hours (midnight cron rush).

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Scheduler API** validates and inserts jobs as SCHEDULED with `next_run_at`.
- **Workers** poll every second: atomically **lease** up to N due jobs (status SCHEDULED and due, or RUNNING with an expired lease) using `FOR UPDATE SKIP LOCKED`, which sets status RUNNING, `lease_owner`, and `lease_expires_at`.
- The worker runs the handler, then **completes** (one-off → SUCCEEDED; recurring → next slot) or **fails** (retry at `now + backoff`, or FAILED after max attempts).
- **Crash recovery:** if a worker dies, its lease expires and another worker re-leases the job. A late report from the dead worker is rejected because it no longer owns the lease.

## API Design
```http
POST /jobs   { "type": "send-email", "payload": "welcome:u42", "runAt": "2026-01-15T10:00:00Z", "maxAttempts": 3 }
POST /jobs   { "type": "cleanup", "payload": "tmp/", "every": "PT10M" }          (or "cron": "0 2 * * *")
→ 201 { "id": "job-1", "status": "SCHEDULED", "nextRunAt": "..." }
DELETE /jobs/job-1   → 204 if still scheduled, 409 if already running
GET    /jobs/job-1   → status, attempts, lastError, nextRunAt
```

## Data Model
**`jobs`** table (PostgreSQL):

| Column | Notes |
|---|---|
| `id`, `type`, `payload` | What to run |
| `next_run_at` | **Indexed** together with `status`; the poller's main query |
| `interval` / `cron`, `timezone` | For recurring jobs |
| `status` | SCHEDULED, RUNNING, SUCCEEDED, FAILED, CANCELLED |
| `attempts`, `max_attempts`, `last_error` | Retry bookkeeping |
| `lease_owner`, `lease_expires_at` | Who is running it, and until when |

Old finished jobs move to an archive table so the hot table stays small.

## Key Design Decisions
| Decision | Why |
|---|---|
| **Leases instead of plain locks** | A lock held by a crashed worker would block the job forever; a lease simply expires. |
| **`FOR UPDATE SKIP LOCKED`** | Many workers can poll the same table concurrently without blocking each other or taking the same rows. |
| **At-least-once + idempotent handlers** | Exactly-once is impossible across crashes (a worker can finish the work and die before recording it). Handlers must tolerate a re-run. |
| **Owner check on completion** | A slow worker whose lease expired can't overwrite the result of the worker that took over. |
| **Exponential backoff with a cap** | Transient failures recover; persistent ones don't hammer downstream systems. |
| **Skip missed recurring runs** | After downtime, run once and resume the cadence instead of replaying a burst of 50 missed runs. |

## Bottlenecks and Trade-offs
- **Polling the DB** at high scale stresses the table. Use a **dispatcher**: a few leader processes move due jobs into Kafka partitions, and workers consume from Kafka instead of polling.
- **Thundering herd at :00** (everyone schedules "midnight"): add jitter to schedules and pre-fetch upcoming jobs into the queue a few seconds early.
- **Long-running jobs** outlive a fixed lease. Workers must heartbeat (extend the lease) while working.
- **Clock skew** between workers affects due checks and lease expiry. Use the database's `now()` as the single clock.
- **Hot table:** shard by job ID hash or time bucket; partition by `next_run_at` day for easy archiving.

## Scaling Strategy
1. **Single process with an in-memory timer** (`ScheduledExecutorService`): fine until it restarts and loses everything.
2. **Jobs in a DB table + one worker** polling.
3. **Many workers** with leases and `SKIP LOCKED`; idempotent handlers; backoff and failure alerts.
4. **Dispatcher + queue:** leader-elected dispatchers move due jobs to Kafka; workers scale independently; time-bucketed partitions.
5. **Multi-region:** home each job in one region, with failover of dispatchers via leader election (ZooKeeper/etcd).
