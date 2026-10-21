package com.sdprep.messagequeue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Manages consumer groups: who is in each group, which partitions each member owns, and how far the
 * group has processed each partition (committed offsets).
 *
 * <p>Rule: within a group, each partition is consumed by exactly one member (preserving per-partition order).
 * Different groups are independent: each gets every message.
 */
public class GroupCoordinator {

    private record GroupTopic(String group, String topic) {
    }

    private record PartitionKey(String group, String topic, int partition) {
    }

    private final Broker broker;
    private final Map<GroupTopic, TreeSet<String>> membersByGroup = new HashMap<>();
    private final Map<GroupTopic, Map<String, List<Integer>>> assignments = new HashMap<>();
    private final Map<PartitionKey, Long> committedOffsets = new HashMap<>(); // production: an internal compacted topic

    public GroupCoordinator(Broker broker) {
        this.broker = broker;
    }

    public synchronized void join(String group, String topic, String consumerId) {
        membersByGroup.computeIfAbsent(new GroupTopic(group, topic), key -> new TreeSet<>()).add(consumerId);
        rebalance(group, topic);
    }

    /** Called on clean shutdown, or when a member misses heartbeats (production: session timeout). */
    public synchronized void leave(String group, String topic, String consumerId) {
        membersByGroup.getOrDefault(new GroupTopic(group, topic), new TreeSet<>()).remove(consumerId);
        rebalance(group, topic);
    }

    public synchronized List<Integer> assignment(String group, String topic, String consumerId) {
        return assignments.getOrDefault(new GroupTopic(group, topic), Map.of()).getOrDefault(consumerId, List.of());
    }

    public synchronized void commit(String group, String topic, int partition, long nextOffsetToRead) {
        committedOffsets.put(new PartitionKey(group, topic, partition), nextOffsetToRead);
    }

    public synchronized Optional<Long> committedOffset(String group, String topic, int partition) {
        return Optional.ofNullable(committedOffsets.get(new PartitionKey(group, topic, partition)));
    }

    /** Range assignment: partitions split into contiguous, near-equal ranges over members sorted by ID. */
    private void rebalance(String group, String topic) {
        GroupTopic key = new GroupTopic(group, topic);
        List<String> members = new ArrayList<>(membersByGroup.getOrDefault(key, new TreeSet<>()));
        Map<String, List<Integer>> newAssignment = new HashMap<>();
        int partitions = broker.partitionCount(topic);
        for (int i = 0; i < members.size(); i++) {
            List<Integer> owned = new ArrayList<>();
            for (int p = 0; p < partitions; p++) {
                if (p * members.size() / partitions == i) {
                    owned.add(p);
                }
            }
            newAssignment.put(members.get(i), owned); // more members than partitions → some get nothing
        }
        assignments.put(key, newAssignment);
    }
}
