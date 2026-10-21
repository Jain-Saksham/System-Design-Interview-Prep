package com.sdprep.distributedcache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheClusterTest {

    private CacheCluster cluster;

    @BeforeEach
    void setUp() {
        cluster = new CacheCluster(100, 2, 100, () -> 0L);
        cluster.addNode("n1");
        cluster.addNode("n2");
        cluster.addNode("n3");
    }

    @Test
    void storesOnEveryReplicaAndReadsBack() {
        cluster.put("k", "v", Long.MAX_VALUE);

        List<String> replicas = cluster.nodesFor("k");
        replicas.forEach(node -> assertEquals(1, cluster.entriesOn(node)));
        assertEquals(Optional.of("v"), cluster.get("k"));
    }

    @Test
    void replicaServesReadsWhenTheOwnerIsDown() {
        cluster.put("k", "v", Long.MAX_VALUE);

        cluster.setNodeUp(cluster.nodesFor("k").get(0), false);

        assertEquals(Optional.of("v"), cluster.get("k"));
    }

    @Test
    void allReplicasDownIsAMiss() {
        cluster.put("k", "v", Long.MAX_VALUE);

        cluster.nodesFor("k").forEach(node -> cluster.setNodeUp(node, false));

        assertTrue(cluster.get("k").isEmpty());
    }

    @Test
    void deleteRemovesFromAllReplicas() {
        cluster.put("k", "v", Long.MAX_VALUE);

        cluster.delete("k");

        cluster.nodesFor("k").forEach(node -> assertEquals(0, cluster.entriesOn(node)));
        assertTrue(cluster.get("k").isEmpty());
    }
}
