package com.sdprep.distributedcache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistentHashRingTest {

    private static final int KEYS = 20_000;

    private ConsistentHashRing ring;

    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(150);
        ring.addNode("n1");
        ring.addNode("n2");
        ring.addNode("n3");
    }

    @Test
    void sameKeyAlwaysMapsToSameNode() {
        assertEquals(ring.nodeFor("user:42"), ring.nodeFor("user:42"));
    }

    @Test
    void virtualNodesKeepTheLoadRoughlyEven() {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < KEYS; i++) {
            counts.merge(ring.nodeFor("key-" + i), 1, Integer::sum);
        }
        double fairShare = KEYS / 3.0;
        counts.values().forEach(count -> assertTrue(Math.abs(count - fairShare) < fairShare * 0.2,
                "node load " + count + " is more than 20% away from " + fairShare));
    }

    @Test
    void addingANodeMovesOnlyAboutAQuarterOfKeys() {
        Map<String, String> before = ownership();

        ring.addNode("n4");

        long moved = before.entrySet().stream().filter(e -> !ring.nodeFor(e.getKey()).equals(e.getValue())).count();
        double fraction = (double) moved / KEYS;
        assertTrue(fraction > 0.15 && fraction < 0.35, "moved " + fraction); // ideal: 1/4
        // ...and every moved key moved to the new node, never between old nodes.
        before.forEach((key, owner) -> {
            String now = ring.nodeFor(key);
            assertTrue(now.equals(owner) || now.equals("n4"));
        });
    }

    @Test
    void removingANodeOnlyMovesItsOwnKeys() {
        Map<String, String> before = ownership();

        ring.removeNode("n2");

        before.forEach((key, owner) -> {
            if (!owner.equals("n2")) {
                assertEquals(owner, ring.nodeFor(key));
            } else {
                assertNotEquals("n2", ring.nodeFor(key));
            }
        });
    }

    @Test
    void replicaNodesAreDistinctAndStartWithTheOwner() {
        List<String> nodes = ring.nodesFor("user:42", 2);

        assertEquals(2, nodes.size());
        assertEquals(ring.nodeFor("user:42"), nodes.get(0));
        assertNotEquals(nodes.get(0), nodes.get(1));
        assertEquals(3, ring.nodesFor("user:42", 10).size()); // can't exceed the number of physical nodes
    }

    private Map<String, String> ownership() {
        Map<String, String> owners = new HashMap<>();
        for (int i = 0; i < KEYS; i++) {
            owners.put("key-" + i, ring.nodeFor("key-" + i));
        }
        return owners;
    }
}
