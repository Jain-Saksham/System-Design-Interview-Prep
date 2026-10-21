package com.sdprep.messagequeue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A member of a consumer group. It reads its assigned partitions from its current position and
 * commits progress explicitly. Committing AFTER processing gives at-least-once delivery: if the consumer
 * crashes before committing, whoever takes over its partitions re-reads those messages.
 */
public class Consumer {

    private final String consumerId;
    private final String group;
    private final String topic;
    private final Broker broker;
    private final GroupCoordinator coordinator;
    private final Map<Integer, Long> positions = new HashMap<>(); // next offset to read, per owned partition

    public Consumer(String consumerId, String group, String topic, Broker broker, GroupCoordinator coordinator) {
        this.consumerId = consumerId;
        this.group = group;
        this.topic = topic;
        this.broker = broker;
        this.coordinator = coordinator;
        coordinator.join(group, topic, consumerId);
    }

    public String id() {
        return consumerId;
    }

    /** Fetches up to {@code maxPerPartition} new records from each partition this consumer owns. */
    public List<Record> poll(int maxPerPartition) {
        List<Integer> owned = coordinator.assignment(group, topic, consumerId);
        positions.keySet().retainAll(owned); // partitions taken away by a rebalance
        List<Record> records = new ArrayList<>();
        for (int partition : owned) {
            long position = positions.computeIfAbsent(partition, this::startingOffset);
            position = Math.max(position, broker.earliestOffset(topic, partition)); // retention may have removed old data
            List<Record> batch = broker.fetch(topic, partition, position, maxPerPartition);
            records.addAll(batch);
            if (!batch.isEmpty()) {
                position = batch.get(batch.size() - 1).offset() + 1;
            }
            positions.put(partition, position);
        }
        return records;
    }

    /** Records progress so a restart or another member continues from here. */
    public void commit() {
        positions.forEach((partition, position) -> coordinator.commit(group, topic, partition, position));
    }

    public void close() {
        coordinator.leave(group, topic, consumerId);
    }

    public List<Integer> assignedPartitions() {
        return coordinator.assignment(group, topic, consumerId);
    }

    /** Resume from the group's committed offset; a brand-new group starts at the earliest retained message. */
    private long startingOffset(int partition) {
        return coordinator.committedOffset(group, topic, partition).orElse(broker.earliestOffset(topic, partition));
    }
}
