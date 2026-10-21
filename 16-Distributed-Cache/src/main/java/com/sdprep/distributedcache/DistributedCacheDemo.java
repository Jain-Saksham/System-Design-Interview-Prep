package com.sdprep.distributedcache;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/** Runnable walkthrough: LRU eviction and TTL, ring balance, keys moved when scaling, replica reads. */
public class DistributedCacheDemo {

    private static final int KEYS = 30_000;

    public static void main(String[] args) {
        lruDemo();
        ringDemo();
        replicationDemo();
    }

    private static void lruDemo() {
        AtomicLong clock = new AtomicLong();
        LruCache<String, String> cache = new LruCache<>(3, clock::get);
        System.out.println("1) LRU cache, capacity 3");
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.get("a"); // a is now most recently used
        cache.put("d", "4");
        System.out.println("   put a,b,c · get a · put d -> b evicted? " + cache.get("b").isEmpty()
                + ", a kept? " + cache.get("a").isPresent());
        cache.put("session", "xyz", 1_000);
        clock.set(1_000);
        System.out.println("   session with 1 s TTL, read after 1 s -> " + cache.get("session").orElse("(expired)") + "\n");
    }

    private static void ringDemo() {
        ConsistentHashRing ring = new ConsistentHashRing(150);
        ring.addNode("cache-1");
        ring.addNode("cache-2");
        ring.addNode("cache-3");
        System.out.println("2) Consistent hashing, 3 nodes × 150 virtual nodes, " + KEYS + " keys");
        System.out.println("   keys per node: " + keysPerNode(ring));

        Map<String, String> ownerBefore = new TreeMap<>();
        for (int i = 0; i < KEYS; i++) {
            ownerBefore.put("key-" + i, ring.nodeFor("key-" + i));
        }
        ring.addNode("cache-4");
        long movedRing = ownerBefore.entrySet().stream()
                .filter(entry -> !ring.nodeFor(entry.getKey()).equals(entry.getValue())).count();
        long movedModulo = ownerBefore.keySet().stream()
                .filter(key -> Math.floorMod(ConsistentHashRing.hash(key), 3) != Math.floorMod(ConsistentHashRing.hash(key), 4))
                .count();
        System.out.printf("   add cache-4 -> consistent hashing moved %.1f%% of keys; hash %% N would move %.1f%%%n",
                100.0 * movedRing / KEYS, 100.0 * movedModulo / KEYS);
        System.out.println("   keys per node: " + keysPerNode(ring) + "\n");
    }

    private static void replicationDemo() {
        CacheCluster cluster = new CacheCluster(150, 2, 1_000, () -> 0L);
        cluster.addNode("cache-1");
        cluster.addNode("cache-2");
        cluster.addNode("cache-3");
        cluster.put("user:42", "{name: Asha}", Long.MAX_VALUE);
        String primary = cluster.nodesFor("user:42").get(0);
        System.out.println("3) Replication factor 2: user:42 stored on " + cluster.nodesFor("user:42"));
        cluster.setNodeUp(primary, false);
        System.out.println("   " + primary + " crashes -> get(user:42) = " + cluster.get("user:42").orElse("(miss)")
                + " (served by the replica)");
    }

    private static Map<String, Integer> keysPerNode(ConsistentHashRing ring) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int i = 0; i < KEYS; i++) {
            counts.merge(ring.nodeFor("key-" + i), 1, Integer::sum);
        }
        return counts;
    }
}
