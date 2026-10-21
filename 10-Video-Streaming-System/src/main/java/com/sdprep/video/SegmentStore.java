package com.sdprep.video;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where transcoded segments live. Production: object storage (S3) as the CDN origin.
 * Here: just the path and size of each segment that would have been produced.
 */
public class SegmentStore {

    private final Map<String, Long> sizeBytesByPath = new ConcurrentHashMap<>();

    public void put(String path, long sizeBytes) {
        sizeBytesByPath.put(path, sizeBytes);
    }

    public boolean contains(String path) {
        return sizeBytesByPath.containsKey(path);
    }

    public int segmentCount() {
        return sizeBytesByPath.size();
    }

    public long totalBytes() {
        return sizeBytesByPath.values().stream().mapToLong(Long::longValue).sum();
    }

    public static String segmentPath(String videoId, Rendition rendition, int segmentIndex) {
        return "videos/%s/%s/seg_%d.ts".formatted(videoId, rendition.name(), segmentIndex);
    }
}
