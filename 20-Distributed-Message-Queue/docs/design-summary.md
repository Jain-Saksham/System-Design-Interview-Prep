# Distributed Message Queue: Design Summary

## Problem Statement
Services need to exchange events asynchronously: an order service publishes "order placed", and billing, email, analytics, and search each react in their own time. A distributed message queue stores these events durably, delivers them to every interested consumer group at high throughput, preserves order where it matters, and survives machine failures.

## Functional Requirements
1. Producers publish messages to named topics, optionally with a key.
2. Multiple independent consumer groups each receive every message.
3. Within a group, work is split across members; each message is processed by one member.
4. Consumers track progress (offsets) and resume after restarts.
5. Messages are retained for a configured time, independent of consumption.

## Non-Functional Requirements
- **High throughput:** millions of messages per second across the cluster.
- **Durability:** no acknowledged message is lost when a broker fails.
- **Ordering:** guaranteed within a partition (and therefore per key).
- **Low latency:** a few milliseconds from produce to consume.
- **Scale (assumed):** 1M messages/s × 1 KB = 1 GB/s ingress; 7-day retention ≈ 600 TB before replication.

## High-Level Design
See [architecture.svg](../architecture/architecture.svg).
- **Topics** are split into **partitions**; each partition is an **append-only log** where every message gets the next **offset**.
- **Producers** choose the partition by `hash(key) % partitions` (same key → same partition → ordered), or spread keyless messages round-robin. They batch and compress for throughput.
- **Brokers** store partitions as segment files. Each partition has a **leader** (serves reads and writes) and **followers** that replicate it. With `acks=all`, a write is acknowledged once all in-sync replicas have it.
- **Consumer groups:** the coordinator assigns each partition to exactly one member of a group, and rebalances when members join, leave, or stop heartbeating. Each group commits its own offsets, so groups are completely independent.
- **Retention** deletes old segments by time or size. Reading never deletes, so new consumers can replay history.

## API Design
```java
RecordMetadata produce(String topic, String key, byte[] value);        // → partition, offset
void subscribe(String topic, String groupId);
List<Record> poll(Duration timeout);                                    // records from assigned partitions
void commit();                                                          // offsets of what has been processed
void seek(int partition, long offset);                                  // replay from any retained offset
```

## Data Model
| Concept | Representation |
|---|---|
| Partition log | Segment files (`00000000000000368769.log`) + offset index + time index |
| Record | `offset`, `timestamp`, `key`, `value`, `headers` |
| Committed offsets | Compacted internal topic: `(group, topic, partition) → next offset` |
| Cluster metadata | Controller (KRaft/ZooKeeper): topics, partition leaders, in-sync replica sets |

## Key Design Decisions
| Decision | Why |
|---|---|
| **Append-only log** | Sequential disk writes and reads are extremely fast; the OS page cache serves hot data; zero-copy transfer to consumers. |
| **Partitions** | The unit of parallelism and ordering: more partitions → more consumers in parallel; order is guaranteed only within one. |
| **Key-based partitioning** | All events for one entity (order, user) stay in order without global ordering. |
| **Consumer-tracked offsets (pull model)** | Consumers read at their own pace, can replay, and the broker doesn't track per-message acks. |
| **Consumer groups** | Queue semantics within a group (work split) and pub/sub semantics across groups (everyone gets everything). |
| **Replication with ISR + `acks=all`** | Durability without waiting for slow, out-of-sync replicas. |
| **Commit after processing** | At-least-once delivery; consumers must be idempotent. Commit before processing gives at-most-once. |

## Bottlenecks and Trade-offs
- **Partition count:** too few limits consumer parallelism; too many increases metadata, open files, and rebalance cost. Partitions are hard to reduce later.
- **Hot keys:** one very active key overloads its partition (ordering forces it there). Sub-key the entity or accept less strict ordering.
- **Rebalance storms:** frequent joins and leaves pause consumption. Use cooperative/incremental rebalancing and static membership.
- **Exactly-once:** needs idempotent producers + transactions + transactional consumers (read-process-write within the queue). Across external systems, use idempotent consumers instead.
- **Consumer lag:** slow consumers fall behind and data may expire. Monitor lag and scale the group (up to the partition count).

## Scaling Strategy
1. **Single broker, single partition per topic** (simple, ordered, limited throughput).
2. **Multiple partitions + consumer groups** for parallelism; key-based partitioning for order.
3. **Broker cluster with replication factor 3**, `acks=all`, min ISR 2; leader election on failure.
4. **Tiered storage:** recent segments on local disk, older ones in object storage; longer retention cheaply.
5. **Multi-region:** separate clusters per region with asynchronous mirroring (MirrorMaker), consumers reading locally.
