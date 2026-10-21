package com.sdprep.messagequeue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageQueueTest {

    private Broker broker;
    private GroupCoordinator coordinator;

    @BeforeEach
    void setUp() {
        broker = new Broker();
        coordinator = new GroupCoordinator(broker);
        broker.createTopic("t", 4);
    }

    @Test
    void sameKeyGoesToSamePartitionInOrder() {
        List<Record> written = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            written.add(broker.produce("t", "order-42", "event-" + i));
        }

        int partition = written.get(0).partition();
        assertTrue(written.stream().allMatch(record -> record.partition() == partition));
        List<String> stored = broker.fetch("t", partition, 0, 10).stream().map(Record::value).toList();
        assertEquals(List.of("event-0", "event-1", "event-2", "event-3", "event-4"), stored);
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L), written.stream().map(Record::offset).toList());
    }

    @Test
    void unkeyedMessagesAreSpreadOverPartitions() {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            used.add(broker.produce("t", null, "m" + i).partition());
        }
        assertEquals(4, used.size());
    }

    @Test
    void groupMembersSplitPartitionsWithoutOverlap() {
        Consumer a = consumer("a", "g");
        Consumer b = consumer("b", "g");

        Set<Integer> all = new HashSet<>(a.assignedPartitions());
        all.addAll(b.assignedPartitions());

        assertEquals(Set.of(0, 1, 2, 3), all);
        assertEquals(2, a.assignedPartitions().size());
        assertTrue(a.assignedPartitions().stream().noneMatch(b.assignedPartitions()::contains));
    }

    @Test
    void eachGroupReceivesEveryMessage() {
        produce(10);

        assertEquals(10, consumer("x", "group-1").poll(100).size());
        assertEquals(10, consumer("y", "group-2").poll(100).size());
    }

    @Test
    void committedProgressSurvivesARestart() {
        produce(6);
        Consumer first = consumer("c1", "g");
        first.poll(100);
        first.commit();
        first.close();
        produce(2);

        Consumer restarted = consumer("c2", "g");

        assertEquals(2, restarted.poll(100).size()); // only the new messages
    }

    @Test
    void uncommittedMessagesAreRedeliveredAfterACrash() {
        produce(6);
        Consumer crashing = consumer("c1", "g");
        assertEquals(6, crashing.poll(100).size());
        crashing.close(); // crashed without committing

        assertEquals(6, consumer("c2", "g").poll(100).size()); // at-least-once
    }

    @Test
    void rebalanceMovesPartitionsWhenMembersJoinAndLeave() {
        Consumer a = consumer("a", "g");
        assertEquals(List.of(0, 1, 2, 3), a.assignedPartitions());

        Consumer b = consumer("b", "g");
        assertEquals(List.of(0, 1), a.assignedPartitions());
        assertEquals(List.of(2, 3), b.assignedPartitions());

        a.close();
        assertEquals(List.of(0, 1, 2, 3), b.assignedPartitions());
    }

    @Test
    void extraConsumersBeyondPartitionCountSitIdle() {
        List<Consumer> consumers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            consumers.add(consumer("c" + i, "g"));
        }
        long idle = consumers.stream().filter(c -> c.assignedPartitions().isEmpty()).count();
        assertEquals(2, idle);
    }

    @Test
    void retentionRemovesOldMessagesAndConsumersSkipAhead() {
        for (int i = 0; i < 5; i++) {
            broker.produce("t", "same-key", "m" + i);
        }
        int partition = Broker.partitionFor("same-key", 4);

        broker.applyRetention("t", 2);

        assertEquals(3, broker.earliestOffset("t", partition));
        List<String> values = consumer("late", "new-group").poll(100).stream().map(Record::value).toList();
        assertEquals(List.of("m3", "m4"), values);
    }

    private Consumer consumer(String id, String group) {
        return new Consumer(id, group, "t", broker, coordinator);
    }

    private void produce(int count) {
        for (int i = 0; i < count; i++) {
            broker.produce("t", "key-" + i, "value-" + i);
        }
    }
}
