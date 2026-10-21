# Logging and Monitoring System: Interview Questions

**1. Estimate ingestion volume.**
Assume 5,000 hosts × 200 lines/s = **1M lines/s**. At ~200 bytes/line: 200 MB/s ≈ **17 TB/day** raw, maybe 2–3 TB/day compressed. Thirty days of searchable logs is roughly 500 TB of index plus replicas, which is why tiering and sampling matter.

**2. Why put Kafka between agents and storage?**
It absorbs bursts (deploys, incidents produce log spikes), decouples producers from slow consumers, lets several consumers (indexer, metrics, archive) read the same stream, and allows replay after a bug or outage.

**3. What happens when the pipeline can't keep up?**
Agents buffer locally (memory, then disk) and retry with backoff. If buffers fill, drop the lowest-value data first (DEBUG), count drops, and alert on the drop count. Never block the application's logging call.

**4. Push or pull for metrics?**
Pull (Prometheus scrapes endpoints) makes discovering dead targets easy and gives the server control over load. Push (StatsD, OTLP) suits short-lived jobs and firewalled hosts. Logs are almost always pushed by agents.

**5. How do you make logs searchable quickly?**
Index recent data into a search engine (inverted index on message, keyword fields for service and level) partitioned by day. Queries filter by time first (partition pruning), then by fields. Older data moves to cheaper storage with slower search.

**6. Why are high-cardinality metric labels dangerous?**
Each unique label combination is a separate time series. A label like `user_id` creates millions of series, exploding memory and storage in the TSDB. Keep labels bounded (service, endpoint, status); put high-cardinality details in logs or traces.

**7. How do you avoid alert fatigue?**
Alert on symptoms users feel (error rate, latency, SLO burn), not on every cause. Use windows and thresholds, cooldowns so one incident pages once, grouping of related alerts, routing to the owning team, and silences during maintenance.

**8. How do you implement retention cheaply?**
Partition by time (daily indices). Deleting a whole partition is O(1) compared with deleting billions of rows. Move partitions through tiers: hot SSD (7 days) → warm (30 days) → archive in object storage (months), then delete.

**9. How do you correlate logs across services for one request?**
Propagate a trace ID (W3C `traceparent`) through every service call and include it in every log line. Search by trace ID to see the whole request; distributed tracing (Jaeger, Tempo) adds timing spans.

**10. What should happen to malformed log lines?**
Don't crash or block the pipeline. Count them per source, store them in a raw or dead-letter index for debugging, and alert the owning team if a service suddenly emits many malformed lines.

**11. How do you protect the system from one noisy service?**
Per-tenant (per-service) quotas and rate limits at ingestion, sampling of verbose levels, separate Kafka partitions or topics per tenant group, and dashboards showing each team's volume and cost.

**12. Logs, metrics, traces: when do you use which?**
Metrics answer "is something wrong?" (cheap, aggregated, alertable). Traces answer "where is it slow or failing?" (per-request path across services). Logs answer "what exactly happened?" (detailed events). A good system links them via trace IDs and labels.

**Likely follow-ups:** sampling strategies (head vs tail sampling for traces) · PII scrubbing in logs · multi-tenant isolation and billing · anomaly detection instead of static thresholds.
