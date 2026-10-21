# Distributed Message Queue: Interview Questions

**1. Estimate broker capacity.**
Assume 1M msgs/s × 1 KB = **1 GB/s** in. With replication factor 3, the cluster writes 3 GB/s. With 7-day retention: 1 GB/s × 604,800 s ≈ **600 TB**, ×3 ≈ 1.8 PB on disk. If one broker sustains ~100 MB/s of replicated writes, that's ~30 brokers, before adding consumer read bandwidth (often several times the write rate).

**2. How is a log-based queue different from a traditional queue (RabbitMQ/SQS)?**
A traditional queue deletes a message once a consumer acks it; the broker tracks per-message state. A log keeps messages for a retention period regardless of consumption; each consumer group tracks its own offset. Logs allow replay and many independent consumers and very high throughput. Traditional queues offer per-message routing, priorities, and delays.

**3. How do you guarantee ordering?**
Order is guaranteed only within a partition. Use the entity ID as the message key so all its events go to the same partition. Global ordering would need one partition, which limits throughput to a single consumer.

**4. How do consumer groups work?**
Each partition is assigned to exactly one consumer within a group, so the group shares the work. Consumers beyond the partition count sit idle. Different groups are independent and each sees every message. Membership changes trigger a rebalance.

**5. At-most-once, at-least-once, exactly-once: how does each happen?**
Commit offsets before processing → at-most-once (a crash loses the in-flight message). Commit after processing → at-least-once (a crash causes redelivery). Exactly-once needs idempotent producers + transactions so the processed output and offset commit happen atomically; with external systems, use idempotent consumers instead.

**6. How does the system avoid losing acknowledged messages?**
Replicate each partition to N brokers. The leader acks a write only after all in-sync replicas (ISR) have it (`acks=all`), and refuses writes if fewer than `min.insync.replicas` are in sync. On leader failure, a new leader is elected from the ISR, so no acked message is lost.

**7. Why is sequential disk I/O so important here?**
Appending to a log and reading it in order is the fastest way to use a disk: no random seeks, efficient OS page caching, and zero-copy `sendfile` to the network. That's why a disk-based log can reach hundreds of MB/s per broker.

**8. How do you choose the number of partitions?**
Enough for the target consumer parallelism and throughput (e.g., target throughput ÷ per-consumer throughput), with headroom, since partitions can be added but keys will remap (breaking per-key order for old data). Avoid huge counts, which increase metadata and rebalance cost.

**9. What happens when a consumer crashes?**
It stops heartbeating. After the session timeout the coordinator rebalances its partitions to other members, which resume from the last committed offsets. Anything processed but not committed is redelivered.

**10. What is consumer lag, and how do you handle it?**
Lag = latest offset − committed offset. Growing lag means consumers can't keep up. Scale the group (up to the partition count), optimize processing, or add partitions. Alert before lag exceeds retention, or data is lost to the consumer.

**11. What is log compaction?**
Instead of deleting by time, keep only the latest message per key, so the topic becomes a durable changelog (e.g., latest profile per user, or committed offsets). Useful for rebuilding state stores.

**12. How do you implement delayed or scheduled messages on a log?**
Logs don't support per-message delays natively. Use delay topics (`retry-1m`, `retry-10m`) consumed after the delay, or a scheduler service that holds messages until due (see [18-Job-Scheduler](../../18-Job-Scheduler/README.md)).

**Likely follow-ups:** dead-letter topics for poison messages · schema evolution (schema registry, Avro/Protobuf) · Kafka vs Pulsar vs SQS trade-offs · how the transactional outbox pattern feeds a queue reliably (see [14-Payment-System](../../14-Payment-System/README.md)).
