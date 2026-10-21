# Logging and Monitoring System: Design Summary

## Problem Statement
With hundreds of services on thousands of machines, engineers can't SSH into boxes to read log files. A centralized system must collect every log line, make it searchable within seconds, turn it into metrics for dashboards, and page someone when error rates spike, without ever slowing down the applications it observes.

## Functional Requirements
1. Collect logs from all services and hosts.
2. Search logs by service, level, time range, and text.
3. Derive metrics (error counts, request rates) and show dashboards.
4. Alert when rules are violated (e.g., error rate above threshold), without spamming.
5. Retain logs for a configured period, then archive or delete.

## Non-Functional Requirements
- **Never hurt the producers:** logging is asynchronous; when overloaded, drop (and count) rather than block.
- **Write-heavy:** ingest far more than is ever read.
- **Freshness:** logs searchable within ~10–30 s; alerts within ~1 minute.
- **Durability:** acceptable loss is small and measured, but should be near zero for error logs.
- **Scale (assumed):** 5,000 hosts × 200 lines/s ≈ 1M lines/s, ~200 bytes each ≈ 200 MB/s ≈ 17 TB/day raw.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Agents** on each host tail files/stdout, batch, compress, and retry (buffering to local disk during outages).
- **Kafka** absorbs bursts and decouples producers from processors; consumers can be replayed.
- **Stream processors** parse lines into structured events, enrich them (host, region, trace ID), and fan out to:
  - a **log index** (Elasticsearch/OpenSearch, Loki) partitioned by day for search,
  - a **metrics TSDB** with counters per service/level/minute for dashboards and alerts,
  - an **archive** in object storage for cheap long-term retention.
- The **alert manager** evaluates rules against metrics every minute and applies cooldowns, grouping, and routing (PagerDuty, Slack).

## API Design
```text
Ingest (agent → collector):  POST /v1/logs  [{ "ts": "...", "service": "checkout", "level": "ERROR", "msg": "...", "trace_id": "..." }, ...]
Search:   GET /v1/logs?service=checkout&level=WARN&from=...&to=...&q=timeout&limit=100
Metrics:  GET /v1/metrics?name=log_lines_total&service=checkout&level=ERROR&step=1m
Alert rule: { "name": "checkout-errors", "expr": "errors(checkout, 2m) >= 5", "cooldown": "10m", "notify": "pagerduty:payments" }
```

## Data Model
| Store | Shape | Notes |
|---|---|---|
| Log index | Documents `{ts, service, host, level, message, trace_id}`; one index per day | Inverted index on message; time-based retention by dropping whole indices |
| Metrics TSDB | Series `(name, labels) → [(minute, value)]` | Low cardinality labels only (service, level), never user IDs |
| Archive | Compressed files per hour per service in object storage | Queried rarely (audits), restored on demand |
| Alert state | `rule → last_fired, state (OK/FIRING)` | Small; needs to survive restarts |

## Key Design Decisions
| Decision | Why |
|---|---|
| **Asynchronous shipping via agents + Kafka** | Apps write locally and move on; a logging outage never becomes an application outage. |
| **Drop and count under overload** | Blocking would stall the app; silently dropping hides problems. Counting makes the loss visible. |
| **Structured logs (JSON fields)** | Enables filtering and aggregation; free-text grepping doesn't scale. |
| **Time-partitioned storage** | Queries almost always have a time range, so prune partitions; retention = drop old partitions. |
| **Metrics from aggregated counters** | Alerts read tiny time series, not billions of raw lines. |
| **Cooldowns and grouping** | One incident should page once, not every minute; alert fatigue makes people ignore pages. |

## Bottlenecks and Trade-offs
- **Indexing cost:** full-text indexing is CPU- and storage-heavy. Index only recent data (hot tier), or use label-only indexing (Loki) with brute-force search over compressed chunks.
- **High-cardinality labels** (user ID as a metric label) explode the TSDB. Keep labels bounded; put details in logs.
- **Burst from one noisy service** can starve others. Use per-tenant quotas and rate limits at ingestion; sample DEBUG logs.
- **Cost:** storage dominates. Tier hot (SSD, 7 days), warm (HDD, 30 days), cold (object storage, 1 year).
- **Alert accuracy:** thresholds that are too tight cause noise, too loose cause misses. Use rate-based rules and SLO burn rates.

## Scaling Strategy
1. **Ship logs to one central server** with a database and grep (small teams).
2. **Agents → Kafka → processors → Elasticsearch;** dashboards on top.
3. **Separate metrics pipeline** (TSDB) and alert manager; derive metrics at ingestion.
4. **Partition everything** by time and tenant; hot/warm/cold tiers; per-tenant quotas.
5. **Multi-region:** regional pipelines; global search via federated queries; cross-region archive.
