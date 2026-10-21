package com.sdprep.filesync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The cloud side: a content-addressed chunk store plus file metadata with a change journal.
 *
 * <p>Production splits this into a block service (chunks in S3) and a metadata service
 * (a sharded SQL database), plus a notification service that tells devices when to sync.
 */
public class SyncServer {

    /** Result of trying to save a new version of a file. */
    public record CommitResult(boolean accepted, FileVersion current) {
    }

    private final Map<String, byte[]> chunksByHash = new HashMap<>();
    private final Map<String, FileVersion> latestByPath = new HashMap<>();
    private final List<FileVersion> journal = new ArrayList<>(); // append-only change log

    // ---- Block service -------------------------------------------------------------------------

    /** Which of these chunks does the server not have yet? Only those need uploading. */
    public synchronized List<String> missingChunks(List<String> hashes) {
        return hashes.stream().distinct().filter(hash -> !chunksByHash.containsKey(hash)).toList();
    }

    public synchronized void uploadChunk(String hash, byte[] data) {
        if (!Chunker.hash(data).equals(hash)) {
            throw new IllegalArgumentException("Chunk content doesn't match its hash (corrupted upload)");
        }
        chunksByHash.put(hash, data.clone());
    }

    public synchronized byte[] downloadChunk(String hash) {
        byte[] data = chunksByHash.get(hash);
        if (data == null) {
            throw new IllegalArgumentException("Unknown chunk " + hash);
        }
        return data.clone();
    }

    public synchronized int storedChunkCount() {
        return chunksByHash.size();
    }

    // ---- Metadata service ----------------------------------------------------------------------

    /**
     * Saves a new version only if the client edited the version the server currently has
     * (optimistic concurrency). Otherwise someone else changed the file first: a conflict.
     *
     * @param baseVersion the version the client started editing from (0 for a new file)
     */
    public synchronized CommitResult commit(String path, long baseVersion, List<String> chunkHashes,
                                            long sizeBytes, boolean deleted, String deviceId) {
        long currentVersion = latest(path).map(FileVersion::version).orElse(0L);
        if (baseVersion != currentVersion) {
            return new CommitResult(false, latestByPath.get(path));
        }
        List<String> missing = missingChunks(chunkHashes);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Upload these chunks before committing: " + missing);
        }
        FileVersion version = new FileVersion(path, currentVersion + 1, List.copyOf(chunkHashes), sizeBytes,
                deleted, deviceId, journal.size() + 1L);
        latestByPath.put(path, version);
        journal.add(version);
        return new CommitResult(true, version);
    }

    /** Every change after {@code cursor}, oldest first. Devices store the last sequence they've seen. */
    public synchronized List<FileVersion> changesSince(long cursor) {
        return List.copyOf(journal.subList((int) Math.min(cursor, journal.size()), journal.size()));
    }

    public synchronized Optional<FileVersion> latest(String path) {
        return Optional.ofNullable(latestByPath.get(path));
    }

    /** Paths that currently exist (not deleted), for the demo and tests. */
    public synchronized List<String> livePaths() {
        return latestByPath.values().stream().filter(version -> !version.deleted())
                .map(FileVersion::path).sorted().toList();
    }
}
