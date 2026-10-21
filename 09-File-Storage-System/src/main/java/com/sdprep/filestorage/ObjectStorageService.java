package com.sdprep.filestorage;

import com.sdprep.filestorage.StorageExceptions.NotFoundException;
import com.sdprep.filestorage.StorageExceptions.UnavailableException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * S3-style API: put / get / delete / list objects in buckets.
 *
 * <p>Write path: split into chunks → store each chunk on N nodes → only then save metadata.
 * Saving metadata last means a reader can never see an object whose bytes aren't fully stored.
 * <br>Read path: load metadata → for each chunk, read the first healthy replica whose checksum matches.
 */
public class ObjectStorageService {

    private final MetadataStore metadata;
    private final List<StorageNode> nodes;
    private final Map<String, StorageNode> nodesById;
    private final int chunkSizeBytes;
    private final int replicationFactor;
    private final LongSupplier clockMillis;

    public ObjectStorageService(MetadataStore metadata, List<StorageNode> nodes, int chunkSizeBytes,
                                int replicationFactor, LongSupplier clockMillis) {
        this.metadata = metadata;
        this.nodes = List.copyOf(nodes);
        this.nodesById = nodes.stream().collect(Collectors.toMap(StorageNode::id, Function.identity()));
        this.chunkSizeBytes = chunkSizeBytes;
        this.replicationFactor = replicationFactor;
        this.clockMillis = clockMillis;
    }

    public void createBucket(String bucket) {
        metadata.createBucket(bucket);
    }

    public ObjectMetadata putObject(String bucket, String key, byte[] data) {
        long previousVersion = metadata.get(bucket, key).map(ObjectMetadata::version).orElse(0L); // also checks the bucket exists
        List<ChunkRef> chunks = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += chunkSizeBytes) {
            byte[] chunk = Arrays.copyOfRange(data, offset, Math.min(offset + chunkSizeBytes, data.length));
            chunks.add(storeChunk(chunk));
        }
        ObjectMetadata object = new ObjectMetadata(bucket, key, data.length, sha256(data), chunks,
                previousVersion + 1, clockMillis.getAsLong());
        metadata.put(object); // the commit point: the object becomes visible only now
        return object;
    }

    public byte[] getObject(String bucket, String key) {
        ObjectMetadata object = headObject(bucket, key);
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) object.sizeBytes());
        for (ChunkRef chunk : object.chunks()) {
            out.writeBytes(readChunk(chunk));
        }
        return out.toByteArray();
    }

    /** Metadata only (like HTTP HEAD). */
    public ObjectMetadata headObject(String bucket, String key) {
        return metadata.get(bucket, key)
                .orElseThrow(() -> new NotFoundException("No such key: " + bucket + "/" + key));
    }

    /**
     * Removes the object from the namespace immediately. Production: chunks are reclaimed later by a
     * garbage collector once no object references them (chunks can be shared across objects).
     */
    public void deleteObject(String bucket, String key) {
        if (!metadata.delete(bucket, key)) {
            throw new NotFoundException("No such key: " + bucket + "/" + key);
        }
    }

    public List<String> listObjects(String bucket, String prefix) {
        return metadata.listKeys(bucket, prefix);
    }

    private ChunkRef storeChunk(byte[] chunk) {
        String chunkId = sha256(chunk);
        List<StorageNode> healthy = nodes.stream().filter(StorageNode::isUp).toList();
        if (healthy.size() < replicationFactor) {
            // Refuse rather than store fewer copies than promised.
            throw new UnavailableException("Need " + replicationFactor + " healthy nodes, have " + healthy.size());
        }
        List<StorageNode> replicas = ReplicaPlacement.choose(chunkId, healthy, replicationFactor);
        for (StorageNode node : replicas) {
            if (!node.hasChunk(chunkId)) { // same content already stored here: deduplicated
                node.write(chunkId, chunk);
            }
        }
        return new ChunkRef(chunkId, chunk.length, replicas.stream().map(StorageNode::id).toList());
    }

    private byte[] readChunk(ChunkRef chunk) {
        for (String nodeId : chunk.replicaNodeIds()) {
            StorageNode node = nodesById.get(nodeId);
            if (!node.isUp()) {
                continue;
            }
            Optional<byte[]> data = node.read(chunk.chunkId());
            if (data.isPresent() && sha256(data.get()).equals(chunk.chunkId())) {
                return data.get(); // checksum matches: this replica is intact
            }
            // Missing or corrupted here. Production also schedules a repair of this replica.
        }
        throw new UnavailableException("No healthy replica for chunk " + chunk.chunkId().substring(0, 8));
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
