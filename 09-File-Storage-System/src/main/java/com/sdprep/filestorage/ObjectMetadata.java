package com.sdprep.filestorage;

import java.util.List;

/**
 * Everything about an object except its bytes. Kept in a separate metadata store from the data,
 * because they have opposite needs: metadata is small and needs strong consistency;
 * data is huge and needs cheap capacity.
 *
 * @param etag SHA-256 of the whole object, so clients can verify downloads
 */
public record ObjectMetadata(String bucket, String key, long sizeBytes, String etag,
                             List<ChunkRef> chunks, long version, long createdAtMillis) {
}
