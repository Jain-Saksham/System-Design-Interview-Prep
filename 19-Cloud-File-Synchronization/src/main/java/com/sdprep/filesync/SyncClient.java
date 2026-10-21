package com.sdprep.filesync;

import com.sdprep.filesync.SyncServer.CommitResult;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One device (laptop, phone) with a local folder. It uploads local changes and pulls remote ones.
 *
 * <p>Upload: chunk → ask which chunks are missing → upload only those → commit with the base version.
 * <br>Pull: fetch journal entries after my cursor → download missing chunks → rebuild files.
 */
public class SyncClient {

    /** What an upload did. {@code conflictCopyPath} is set if the server had a newer version. */
    public record UploadResult(int chunksTotal, int chunksUploaded, Optional<String> conflictCopyPath) {
    }

    private final String deviceId;
    private final SyncServer server;
    private final int chunkSize;
    private final Map<String, byte[]> localFiles = new TreeMap<>();
    private final Map<String, Long> knownVersions = new TreeMap<>(); // version each local file is based on
    private long cursor; // last journal sequence applied

    public SyncClient(String deviceId, SyncServer server, int chunkSize) {
        this.deviceId = deviceId;
        this.server = server;
        this.chunkSize = chunkSize;
    }

    public void writeLocal(String path, byte[] content) {
        localFiles.put(path, content.clone());
    }

    public Optional<byte[]> readLocal(String path) {
        return Optional.ofNullable(localFiles.get(path)).map(byte[]::clone);
    }

    public List<String> localPaths() {
        return List.copyOf(localFiles.keySet());
    }

    public UploadResult upload(String path) {
        byte[] content = localFiles.get(path);
        List<byte[]> chunks = Chunker.split(content, chunkSize);
        List<String> hashes = chunks.stream().map(Chunker::hash).toList();

        List<String> missing = server.missingChunks(hashes);
        for (int i = 0; i < chunks.size(); i++) {
            if (missing.contains(hashes.get(i))) {
                server.uploadChunk(hashes.get(i), chunks.get(i)); // delta sync: unchanged chunks never move
            }
        }

        CommitResult result = server.commit(path, knownVersions.getOrDefault(path, 0L), hashes, content.length, false, deviceId);
        if (result.accepted()) {
            knownVersions.put(path, result.current().version());
            return new UploadResult(chunks.size(), missing.size(), Optional.empty());
        }
        // Conflict: someone else saved first. Keep both: their version stays at `path`,
        // ours becomes a conflicted copy. No edit is ever silently lost.
        String copyPath = conflictCopyName(path);
        localFiles.put(copyPath, content);
        localFiles.remove(path);
        server.commit(copyPath, 0, hashes, content.length, false, deviceId);
        knownVersions.put(copyPath, 1L);
        pullChanges(); // fetch the winning version of `path`
        return new UploadResult(chunks.size(), missing.size(), Optional.of(copyPath));
    }

    public void deleteAndUpload(String path) {
        localFiles.remove(path);
        CommitResult result = server.commit(path, knownVersions.getOrDefault(path, 0L), List.of(), 0, true, deviceId);
        if (result.accepted()) {
            knownVersions.remove(path);
        }
    }

    /** Applies every remote change since the last sync. Returns how many files changed locally. */
    public int pullChanges() {
        int applied = 0;
        for (FileVersion change : server.changesSince(cursor)) {
            cursor = change.journalSequence();
            if (change.version() <= knownVersions.getOrDefault(change.path(), 0L)) {
                continue; // we already have this version (often our own upload)
            }
            if (change.deleted()) {
                localFiles.remove(change.path());
                knownVersions.remove(change.path());
            } else {
                localFiles.put(change.path(), rebuild(change.chunkHashes()));
                knownVersions.put(change.path(), change.version());
            }
            applied++;
        }
        return applied;
    }

    private byte[] rebuild(List<String> chunkHashes) {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        chunkHashes.forEach(hash -> content.writeBytes(server.downloadChunk(hash))); // production: local chunk cache first
        return content.toByteArray();
    }

    private String conflictCopyName(String path) {
        int dot = path.lastIndexOf('.');
        String suffix = " (conflicted copy from " + deviceId + ")";
        return dot < 0 ? path + suffix : path.substring(0, dot) + suffix + path.substring(dot);
    }
}
