package com.sdprep.filestorage;

import java.util.Comparator;
import java.util.List;

/**
 * Chooses which nodes store a chunk using rendezvous (highest-random-weight) hashing:
 * score every node by hash(chunkId + nodeId) and take the top N.
 *
 * <p>Any server can compute the same placement without a lookup table, and adding a node only
 * moves the chunks for which the new node now scores in the top N.
 */
public final class ReplicaPlacement {

    private ReplicaPlacement() {
    }

    public static List<StorageNode> choose(String chunkId, List<StorageNode> healthyNodes, int replicas) {
        return healthyNodes.stream()
                .sorted(Comparator.comparingLong((StorageNode node) -> score(chunkId, node.id())).reversed())
                .limit(replicas)
                .toList();
    }

    private static long score(String chunkId, String nodeId) {
        // A 64-bit mix of the pair; any good hash works. (String.hashCode alone is too weak.)
        long hash = (chunkId + "|" + nodeId).hashCode() * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 31;
        return hash * 0xBF58476D1CE4E5B9L;
    }
}
