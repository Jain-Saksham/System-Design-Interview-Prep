package com.sdprep.filestorage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One data server holding chunks by ID. Production: a machine with many disks storing chunks as files.
 * Here: a map, plus switches to simulate an outage or disk corruption.
 */
public class StorageNode {

    private final String id;
    private final Map<String, byte[]> chunksById = new ConcurrentHashMap<>();
    private volatile boolean up = true;

    public StorageNode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean isUp() {
        return up;
    }

    public void setUp(boolean up) {
        this.up = up;
    }

    public void write(String chunkId, byte[] data) {
        requireUp();
        chunksById.put(chunkId, data.clone());
    }

    public Optional<byte[]> read(String chunkId) {
        requireUp();
        byte[] data = chunksById.get(chunkId);
        return data == null ? Optional.empty() : Optional.of(data.clone());
    }

    public boolean hasChunk(String chunkId) {
        return up && chunksById.containsKey(chunkId);
    }

    public int chunkCount() {
        return chunksById.size();
    }

    /** Test/demo helper: flips one byte, as a failing disk might. */
    public void corrupt(String chunkId) {
        byte[] data = chunksById.get(chunkId);
        if (data != null && data.length > 0) {
            data[0] ^= 0x7F;
        }
    }

    private void requireUp() {
        if (!up) {
            throw new IllegalStateException("Node " + id + " is down");
        }
    }
}
