package com.sdprep.video;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Upload a video, then serve its playlists once processing has finished. */
public class VideoService {

    /** The video exists but isn't playable yet → HTTP 409/425. */
    public static class NotReadyException extends RuntimeException {
        NotReadyException(String videoId) {
            super("Video " + videoId + " is still processing");
        }
    }

    private final TranscodingPipeline pipeline;
    private final double segmentSeconds;
    private final List<Rendition> ladder;
    private final Map<String, Video> videosById = new ConcurrentHashMap<>(); // production: metadata DB
    private final AtomicLong nextId = new AtomicLong(1);

    public VideoService(TranscodingPipeline pipeline, double segmentSeconds, List<Rendition> ladder) {
        this.pipeline = pipeline;
        this.segmentSeconds = segmentSeconds;
        this.ladder = List.copyOf(ladder);
    }

    /** Production: the client uploads the original file to object storage first, then calls this. */
    public Video upload(String title, double durationSeconds) {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        Video video = new Video("v" + nextId.getAndIncrement(), title, durationSeconds);
        videosById.put(video.id(), video);
        pipeline.submit(video);
        return video;
    }

    public Video get(String videoId) {
        Video video = videosById.get(videoId);
        if (video == null) {
            throw new IllegalArgumentException("Unknown video: " + videoId);
        }
        return video;
    }

    public String masterPlaylist(String videoId) {
        requireReady(videoId);
        return HlsManifests.masterPlaylist(ladder);
    }

    /**
     * The same for every rendition: segment names are relative to the rendition's folder
     * ({@code 720p/playlist.m3u8} → {@code 720p/seg_0.ts}), and all renditions share segment boundaries.
     */
    public String mediaPlaylist(String videoId) {
        return HlsManifests.mediaPlaylist(requireReady(videoId), segmentSeconds);
    }

    private Video requireReady(String videoId) {
        Video video = get(videoId);
        if (video.status() != Video.Status.READY) {
            throw new NotReadyException(videoId);
        }
        return video;
    }
}
