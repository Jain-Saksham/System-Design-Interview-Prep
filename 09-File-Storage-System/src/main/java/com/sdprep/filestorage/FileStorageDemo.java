package com.sdprep.filestorage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

/** Runnable walkthrough: chunking, 3× replication, node failure, corruption, dedup, prefix listing. */
public class FileStorageDemo {

    public static void main(String[] args) {
        List<StorageNode> nodes = IntStream.rangeClosed(1, 5).mapToObj(i -> new StorageNode("node-" + i)).toList();
        ObjectStorageService storage = new ObjectStorageService(new MetadataStore(), nodes, 16, 3, () -> 0L);
        System.out.println("5 storage nodes, 16-byte chunks (tiny, to show chunking), 3 replicas per chunk\n");

        storage.createBucket("photos");
        byte[] content = "A picture is worth a thousand words.".getBytes(StandardCharsets.UTF_8);
        ObjectMetadata cat = storage.putObject("photos", "2026/cat.jpg", content);
        System.out.printf("PUT photos/2026/cat.jpg (%d bytes) -> %d chunks, version %d%n",
                cat.sizeBytes(), cat.chunks().size(), cat.version());
        cat.chunks().forEach(chunk -> System.out.printf("  chunk %s… (%2d B) on %s%n",
                chunk.chunkId().substring(0, 8), chunk.sizeBytes(), chunk.replicaNodeIds()));

        String firstReplica = cat.chunks().get(0).replicaNodeIds().get(0);
        nodes.stream().filter(node -> node.id().equals(firstReplica)).findFirst().orElseThrow().setUp(false);
        System.out.println("\n" + firstReplica + " goes down...");
        System.out.println("GET -> \"" + new String(storage.getObject("photos", "2026/cat.jpg"), StandardCharsets.UTF_8)
                + "\" (read from the next replica)");

        String chunkId = cat.chunks().get(1).chunkId();
        String secondReplica = cat.chunks().get(1).replicaNodeIds().get(0);
        nodes.stream().filter(node -> node.id().equals(secondReplica)).findFirst().orElseThrow().corrupt(chunkId);
        System.out.println("\nA disk on " + secondReplica + " silently corrupts chunk " + chunkId.substring(0, 8) + "…");
        boolean intact = new String(storage.getObject("photos", "2026/cat.jpg"), StandardCharsets.UTF_8)
                .equals(new String(content, StandardCharsets.UTF_8));
        System.out.println("GET -> intact=" + intact + " (checksum mismatch detected, other replica used)");

        nodes.stream().filter(node -> node.id().equals(firstReplica)).findFirst().orElseThrow().setUp(true);
        System.out.println("\n" + firstReplica + " recovers.");
        int storedBefore = nodes.stream().mapToInt(StorageNode::chunkCount).sum();
        storage.putObject("photos", "2026/cat-copy.jpg", content);
        int storedAfter = nodes.stream().mapToInt(StorageNode::chunkCount).sum();
        System.out.printf("PUT an identical file as cat-copy.jpg -> new chunk copies stored: %d (content-addressed dedup)%n",
                storedAfter - storedBefore);

        storage.putObject("photos", "2025/dog.jpg", "woof".getBytes(StandardCharsets.UTF_8));
        System.out.println("LIST photos/2026/ -> " + storage.listObjects("photos", "2026/"));
    }
}
