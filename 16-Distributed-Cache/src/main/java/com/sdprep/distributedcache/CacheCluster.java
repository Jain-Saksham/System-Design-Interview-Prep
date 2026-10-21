package com.sdprep.distributedcache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Client-side view of a cache cluster (like a memcached/Redis Cluster client): it routes each key to its
 * nodes on the hash ring, writes to every replica, and reads from the first healthy replica that has it.
 *
 * <p>Here every "node" is an {@link LruCache} in this JVM. In production each is a separate server,
 * and the ring membership comes from configuration or a coordination service.
 */
public class CacheCluster {

    private static final class CacheNode {
        final LruCache<String, String> cache;
        volatile boolean up = true;

        CacheNode(LruCache<String, String> cache) {
            this.cache = cache;
        }
    }

    private final ConsistentHashRing ring;
    private final int replicas;
    private final int capacityPerNode;
    private final LongSupplier clockMillis;
    private final Map<String, CacheNode> nodesById = new LinkedHashMap<>();

    public CacheCluster(int virtualNodesPerNode, int replicas, int capacityPerNode, LongSupplier clockMillis) {
        this.ring = new ConsistentHashRing(virtualNodesPerNode);
        this.replicas = replicas;
        this.capacityPerNode = capacityPerNode;
        this.clockMillis = clockMillis;
    }

    public synchronized void addNode(String nodeId) {
        nodesById.put(nodeId, new CacheNode(new LruCache<>(capacityPerNode, clockMillis)));
        ring.addNode(nodeId);
    }

    /** Permanently removes a node; its keys become cache misses and are re-populated from the database. */
    public synchronized void removeNode(String nodeId) {
        ring.removeNode(nodeId);
        nodesById.remove(nodeId);
    }

    /** Simulates a crash or network partition without changing ring membership. */
    public void setNodeUp(String nodeId, boolean up) {
        nodesById.get(nodeId).up = up;
    }

    public void put(String key, String value, long ttlMillis) {
        for (String nodeId : ring.nodesFor(key, replicas)) {
            CacheNode node = nodesById.get(nodeId);
            if (node.up) {
                node.cache.put(key, value, ttlMillis);
            }
        }
    }

    public Optional<String> get(String key) {
        for (String nodeId : ring.nodesFor(key, replicas)) {
            CacheNode node = nodesById.get(nodeId);
            if (node.up) {
                Optional<String> value = node.cache.get(key);
                if (value.isPresent()) {
                    return value;
                }
            }
        }
        return Optional.empty(); // a miss: the caller loads from the database and calls put()
    }

    public void delete(String key) {
        for (String nodeId : ring.nodesFor(key, replicas)) {
            CacheNode node = nodesById.get(nodeId);
            if (node.up) {
                node.cache.remove(key);
            }
        }
    }

    public List<String> nodesFor(String key) {
        return ring.nodesFor(key, replicas);
    }

    public int entriesOn(String nodeId) {
        return nodesById.get(nodeId).cache.size();
    }
}
