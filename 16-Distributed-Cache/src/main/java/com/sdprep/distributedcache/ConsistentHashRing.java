package com.sdprep.distributedcache;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Maps keys to nodes so that adding or removing a node moves only ~1/N of the keys.
 * (With {@code hash(key) % N}, changing N moves almost every key.)
 *
 * <p>Nodes and keys are hashed onto the same circle; a key belongs to the first node clockwise.
 * Each physical node is placed many times ("virtual nodes"), which evens out the load.
 */
public class ConsistentHashRing {

    private final int virtualNodesPerNode;
    private final NavigableMap<Long, String> ring = new TreeMap<>();

    public ConsistentHashRing(int virtualNodesPerNode) {
        this.virtualNodesPerNode = virtualNodesPerNode;
    }

    public synchronized void addNode(String nodeId) {
        for (int i = 0; i < virtualNodesPerNode; i++) {
            ring.put(hash(nodeId + "#" + i), nodeId);
        }
    }

    public synchronized void removeNode(String nodeId) {
        for (int i = 0; i < virtualNodesPerNode; i++) {
            ring.remove(hash(nodeId + "#" + i));
        }
    }

    /** The node that owns {@code key}. */
    public synchronized String nodeFor(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No nodes in the ring");
        }
        Map.Entry<Long, String> owner = ring.ceilingEntry(hash(key));
        return owner != null ? owner.getValue() : ring.firstEntry().getValue(); // wrap around the circle
    }

    /** The owner plus the next distinct nodes clockwise: where replicas of {@code key} live. */
    public synchronized List<String> nodesFor(String key, int count) {
        Set<String> nodes = new LinkedHashSet<>();
        long position = hash(key);
        List<String> clockwise = new ArrayList<>(ring.tailMap(position, true).values());
        clockwise.addAll(ring.headMap(position, false).values());
        for (String node : clockwise) {
            nodes.add(node);
            if (nodes.size() == count) {
                break;
            }
        }
        return List.copyOf(nodes);
    }

    /** MD5 spreads keys evenly and gives the same result on every machine (unlike String.hashCode). */
    static long hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is always available in the JDK", e);
        }
    }
}
