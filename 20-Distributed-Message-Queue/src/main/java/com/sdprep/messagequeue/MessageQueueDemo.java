package com.sdprep.messagequeue;

import java.util.List;
import java.util.stream.Collectors;

/** Runnable walkthrough: keyed partitioning, consumer groups, rebalancing, crash redelivery, retention. */
public class MessageQueueDemo {

    public static void main(String[] args) {
        Broker broker = new Broker();
        GroupCoordinator coordinator = new GroupCoordinator(broker);
        broker.createTopic("orders", 3);

        System.out.println("Producing to topic 'orders' (3 partitions), keyed by customer:");
        String[][] events = {{"alice", "created"}, {"erin", "created"}, {"asha", "created"},
                {"alice", "paid"}, {"erin", "paid"}, {"alice", "shipped"}};
        for (String[] event : events) {
            Record record = broker.produce("orders", event[0], event[0] + ":" + event[1]);
            System.out.printf("  %-14s -> partition %d, offset %d%n", record.value(), record.partition(), record.offset());
        }
        System.out.println("  (all of alice's events share a partition, so their order is preserved)\n");

        Consumer billing1 = new Consumer("billing-1", "billing", "orders", broker, coordinator);
        System.out.println("Group 'billing': billing-1 joins -> owns partitions " + billing1.assignedPartitions());
        Consumer billing2 = new Consumer("billing-2", "billing", "orders", broker, coordinator);
        System.out.println("billing-2 joins -> rebalance: billing-1 owns " + billing1.assignedPartitions()
                + ", billing-2 owns " + billing2.assignedPartitions());

        List<Record> firstBatch = billing1.poll(10);
        System.out.println("billing-1 polls " + values(firstBatch) + " ... and CRASHES before committing");
        billing1.close(); // production: detected by missed heartbeats
        System.out.println("rebalance -> billing-2 owns " + billing2.assignedPartitions());
        System.out.println("billing-2 polls " + values(billing2.poll(10)) + " (billing-1's messages redelivered: at-least-once)");
        billing2.commit();

        Consumer analytics = new Consumer("analytics-1", "analytics", "orders", broker, coordinator);
        System.out.println("\nGroup 'analytics' reads independently -> " + analytics.poll(10).size() + " of 6 messages");

        broker.produce("orders", "kiran", "kiran:created");
        System.out.println("New message produced -> billing-2 polls " + values(billing2.poll(10)) + " (continues from its commit)");

        int removed = broker.applyRetention("orders", 1);
        Consumer audit = new Consumer("audit-1", "audit", "orders", broker, coordinator);
        System.out.println("\nRetention keeps the newest message per partition (" + removed + " removed).");
        System.out.println("A brand-new group 'audit' starts at the earliest retained offsets -> " + values(audit.poll(10)));
    }

    private static String values(List<Record> records) {
        return records.stream().map(Record::value).collect(Collectors.joining(", ", "[", "]"));
    }
}
