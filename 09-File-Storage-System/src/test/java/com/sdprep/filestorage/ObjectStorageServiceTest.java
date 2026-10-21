package com.sdprep.filestorage;

import com.sdprep.filestorage.StorageExceptions.NotFoundException;
import com.sdprep.filestorage.StorageExceptions.UnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static com.sdprep.filestorage.ObjectStorageService.utf8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectStorageServiceTest {

    private static final int CHUNK_SIZE = 4;
    private static final int REPLICAS = 3;

    private List<StorageNode> nodes;
    private ObjectStorageService storage;

    @BeforeEach
    void setUp() {
        nodes = IntStream.rangeClosed(1, 5).mapToObj(i -> new StorageNode("n" + i)).toList();
        storage = new ObjectStorageService(new MetadataStore(), nodes, CHUNK_SIZE, REPLICAS, () -> 0L);
        storage.createBucket("b");
    }

    @Test
    void roundTripsContentAcrossChunks() {
        byte[] data = utf8("hello object storage");

        ObjectMetadata meta = storage.putObject("b", "k", data);

        assertEquals(5, meta.chunks().size()); // 20 bytes / 4-byte chunks
        assertArrayEquals(data, storage.getObject("b", "k"));
    }

    @Test
    void emptyObjectIsAllowed() {
        storage.putObject("b", "empty", new byte[0]);
        assertArrayEquals(new byte[0], storage.getObject("b", "empty"));
    }

    @Test
    void eachChunkIsStoredOnDistinctReplicaNodes() {
        ObjectMetadata meta = storage.putObject("b", "k", utf8("abcdefgh"));

        for (ChunkRef chunk : meta.chunks()) {
            assertEquals(REPLICAS, new HashSet<>(chunk.replicaNodeIds()).size());
        }
    }

    @Test
    void readSurvivesOneReplicaNodeBeingDown() {
        byte[] data = utf8("survives failures");
        ObjectMetadata meta = storage.putObject("b", "k", data);

        node(meta.chunks().get(0).replicaNodeIds().get(0)).setUp(false);

        assertArrayEquals(data, storage.getObject("b", "k"));
    }

    @Test
    void corruptedReplicaIsDetectedAndSkipped() {
        byte[] data = utf8("checksums!");
        ObjectMetadata meta = storage.putObject("b", "k", data);
        ChunkRef chunk = meta.chunks().get(0);

        node(chunk.replicaNodeIds().get(0)).corrupt(chunk.chunkId());

        assertArrayEquals(data, storage.getObject("b", "k"));
    }

    @Test
    void readFailsWhenEveryReplicaIsDown() {
        ObjectMetadata meta = storage.putObject("b", "k", utf8("abcd"));
        meta.chunks().get(0).replicaNodeIds().forEach(id -> node(id).setUp(false));

        assertThrows(UnavailableException.class, () -> storage.getObject("b", "k"));
    }

    @Test
    void writeIsRefusedWithoutEnoughHealthyNodes() {
        nodes.get(0).setUp(false);
        nodes.get(1).setUp(false);
        nodes.get(2).setUp(false);

        assertThrows(UnavailableException.class, () -> storage.putObject("b", "k", utf8("data")));
        assertThrows(NotFoundException.class, () -> storage.headObject("b", "k")); // nothing half-written
    }

    @Test
    void overwriteCreatesNewVersion() {
        storage.putObject("b", "k", utf8("v1"));
        ObjectMetadata second = storage.putObject("b", "k", utf8("v2"));

        assertEquals(2, second.version());
        assertArrayEquals(utf8("v2"), storage.getObject("b", "k"));
    }

    @Test
    void identicalContentIsStoredOnce() {
        storage.putObject("b", "first", utf8("same bytes"));
        int storedBefore = nodes.stream().mapToInt(StorageNode::chunkCount).sum();

        storage.putObject("b", "second", utf8("same bytes"));

        assertEquals(storedBefore, nodes.stream().mapToInt(StorageNode::chunkCount).sum());
    }

    @Test
    void listsKeysByPrefixInOrder() {
        storage.putObject("b", "logs/2026/02", utf8("x"));
        storage.putObject("b", "logs/2026/01", utf8("x"));
        storage.putObject("b", "logs/2025/12", utf8("x"));
        storage.putObject("b", "photos/1", utf8("x"));

        assertEquals(List.of("logs/2026/01", "logs/2026/02"), storage.listObjects("b", "logs/2026/"));
    }

    @Test
    void deletedAndMissingObjectsAreNotFound() {
        storage.putObject("b", "k", utf8("x"));
        storage.deleteObject("b", "k");

        assertThrows(NotFoundException.class, () -> storage.getObject("b", "k"));
        assertThrows(NotFoundException.class, () -> storage.putObject("no-such-bucket", "k", utf8("x")));
    }

    private StorageNode node(String id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow();
    }
}
