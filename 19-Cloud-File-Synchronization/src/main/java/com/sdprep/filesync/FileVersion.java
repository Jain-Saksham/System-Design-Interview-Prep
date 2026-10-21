package com.sdprep.filesync;

import java.util.List;

/**
 * One version of one file: the ordered list of chunk hashes that make up its content.
 *
 * @param journalSequence position in the server's change journal; clients sync "everything after N"
 * @param deleted         a tombstone, so other devices learn the file was deleted
 */
public record FileVersion(String path, long version, List<String> chunkHashes, long sizeBytes,
                          boolean deleted, String deviceId, long journalSequence) {
}
