package com.sdprep.filestorage;

import com.sdprep.filestorage.StorageExceptions.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Bucket → key → metadata. Keys are kept sorted so "list by prefix" is a range scan.
 *
 * <p>Production: a strongly consistent, replicated database (e.g. sharded PostgreSQL or a
 * Spanner/FoundationDB-style store), partitioned by bucket + key.
 */
public class MetadataStore {

    private final Map<String, NavigableMap<String, ObjectMetadata>> objectsByBucket = new HashMap<>();

    public synchronized void createBucket(String bucket) {
        objectsByBucket.putIfAbsent(bucket, new TreeMap<>());
    }

    public synchronized void put(ObjectMetadata metadata) {
        bucket(metadata.bucket()).put(metadata.key(), metadata);
    }

    public synchronized Optional<ObjectMetadata> get(String bucket, String key) {
        return Optional.ofNullable(bucket(bucket).get(key));
    }

    public synchronized boolean delete(String bucket, String key) {
        return bucket(bucket).remove(key) != null;
    }

    /** Keys starting with {@code prefix}, in sorted order. */
    public synchronized List<String> listKeys(String bucket, String prefix) {
        return bucket(bucket).tailMap(prefix, true).keySet().stream()
                .takeWhile(key -> key.startsWith(prefix)) // sorted, so matches are contiguous
                .toList();
    }

    private NavigableMap<String, ObjectMetadata> bucket(String bucket) {
        NavigableMap<String, ObjectMetadata> objects = objectsByBucket.get(bucket);
        if (objects == null) {
            throw new NotFoundException("No such bucket: " + bucket);
        }
        return objects;
    }
}
