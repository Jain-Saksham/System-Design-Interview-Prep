package com.sdprep.filestorage;

import java.util.List;

/**
 * Where one piece of an object lives.
 *
 * @param chunkId    SHA-256 of the chunk's bytes: identical chunks share one ID (deduplication),
 *                   and the ID doubles as a checksum to detect corruption
 * @param replicaNodeIds nodes holding a copy, in preferred read order
 */
public record ChunkRef(String chunkId, int sizeBytes, List<String> replicaNodeIds) {
}
