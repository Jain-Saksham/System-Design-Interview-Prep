package com.sdprep.video;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Splits a video into fixed-length segments and creates one job per (segment, rendition).
 * The jobs are independent, which is why production can transcode a 2-hour movie in minutes
 * by spreading them over many workers.
 *
 * <p>"Transcoding" is simulated: each job records a segment whose size matches its bitrate × duration.
 */
public class TranscodingPipeline {

    /** One unit of work for a transcoding worker. */
    public record TranscodeJob(Video video, Rendition rendition, int segmentIndex) {
    }

    private final SegmentStore segmentStore;
    private final double segmentSeconds;
    private final List<Rendition> ladder;
    private final Queue<TranscodeJob> jobs = new ArrayDeque<>();           // production: a durable queue
    private final Map<String, Integer> remainingJobsByVideo = new ConcurrentHashMap<>();

    public TranscodingPipeline(SegmentStore segmentStore, double segmentSeconds, List<Rendition> ladder) {
        this.segmentStore = segmentStore;
        this.segmentSeconds = segmentSeconds;
        this.ladder = List.copyOf(ladder);
    }

    public synchronized int submit(Video video) {
        int segments = video.segmentCount(segmentSeconds);
        for (int segment = 0; segment < segments; segment++) {
            for (Rendition rendition : ladder) {
                jobs.add(new TranscodeJob(video, rendition, segment));
            }
        }
        remainingJobsByVideo.put(video.id(), segments * ladder.size());
        return segments * ladder.size();
    }

    /** Runs one job. Returns false when the queue is empty. */
    public synchronized boolean processNext() {
        TranscodeJob job = jobs.poll();
        if (job == null) {
            return false;
        }
        double seconds = job.video().segmentDuration(job.segmentIndex(), segmentSeconds);
        long sizeBytes = Math.round(job.rendition().bitrateKbps() * 1000 / 8.0 * seconds);
        segmentStore.put(SegmentStore.segmentPath(job.video().id(), job.rendition(), job.segmentIndex()), sizeBytes);

        int remaining = remainingJobsByVideo.merge(job.video().id(), -1, Integer::sum);
        if (remaining == 0) {
            job.video().markReady(); // last job done: every segment exists in every rendition
        }
        return true;
    }

    public int processAll() {
        int processed = 0;
        while (processNext()) {
            processed++;
        }
        return processed;
    }

    public synchronized int pendingJobs() {
        return jobs.size();
    }
}
