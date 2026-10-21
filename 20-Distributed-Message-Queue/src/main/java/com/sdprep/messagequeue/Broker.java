package com.sdprep.messagequeue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/**
 * Stores topics, each split into partitions. Producers append; consumers read by offset.
 *
 * <p>Here one broker holds everything. Production spreads partitions over many brokers: each partition
 * has a leader (handles reads/writes) and followers that replicate it for fault tolerance.
 */
public class Broker {

    private final Map<String, List<Partition>> topics = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobin = new AtomicInteger();

    public void createTopic(String topic, int partitions) {
        List<Partition> list = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            list.add(new Partition(topic, i));
        }
        topics.put(topic, List.copyOf(list));
    }

    public int partitionCount(String topic) {
        return partitions(topic).size();
    }

    /**
     * Same key → same partition → strict order for that key (e.g. all events of one order).
     * Messages without a key are spread round-robin for even load.
     */
    public Record produce(String topic, String key, String value) {
        List<Partition> partitions = partitions(topic);
        int partition = key == null
                ? Math.floorMod(roundRobin.getAndIncrement(), partitions.size())
                : partitionFor(key, partitions.size());
        return partitions.get(partition).append(key, value);
    }

    public List<Record> fetch(String topic, int partition, long fromOffset, int maxRecords) {
        return partitions(topic).get(partition).read(fromOffset, maxRecords);
    }

    public long earliestOffset(String topic, int partition) {
        return partitions(topic).get(partition).earliestOffset();
    }

    public long endOffset(String topic, int partition) {
        return partitions(topic).get(partition).endOffset();
    }

    /** Applies retention to every partition of a topic. Returns how many records were removed. */
    public int applyRetention(String topic, int maxRecordsPerPartition) {
        return partitions(topic).stream().mapToInt(partition -> partition.applyRetention(maxRecordsPerPartition)).sum();
    }

    static int partitionFor(String key, int partitionCount) {
        CRC32 crc = new CRC32(); // stable across JVMs and runs, unlike String.hashCode in some languages
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % partitionCount);
    }

    private List<Partition> partitions(String topic) {
        List<Partition> partitions = topics.get(topic);
        if (partitions == null) {
            throw new IllegalArgumentException("Unknown topic: " + topic);
        }
        return partitions;
    }
}
