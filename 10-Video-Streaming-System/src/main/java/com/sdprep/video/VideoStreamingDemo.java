package com.sdprep.video;

import java.util.List;

/** Runnable walkthrough: upload → transcode → playlists → a simulated adaptive-bitrate session. */
public class VideoStreamingDemo {

    private static final double SEGMENT_SECONDS = 6;

    public static void main(String[] args) {
        List<Rendition> ladder = Rendition.STANDARD_LADDER;
        SegmentStore segments = new SegmentStore();
        TranscodingPipeline pipeline = new TranscodingPipeline(segments, SEGMENT_SECONDS, ladder);
        VideoService videos = new VideoService(pipeline, SEGMENT_SECONDS, ladder);

        Video video = videos.upload("Intro to System Design", 20);
        System.out.printf("Uploaded %s (20 s) -> %d transcode jobs (4 segments × 4 renditions), status %s%n",
                video.id(), pipeline.pendingJobs(), video.status());
        try {
            videos.masterPlaylist(video.id());
        } catch (VideoService.NotReadyException e) {
            System.out.println("Play request -> rejected: " + e.getMessage());
        }

        pipeline.processAll();
        System.out.printf("Workers finished -> status %s, %d segments stored (%.1f MB)%n%n",
                video.status(), segments.segmentCount(), segments.totalBytes() / 1_000_000.0);

        System.out.println("master.m3u8:");
        System.out.print(videos.masterPlaylist(video.id()).indent(2));
        System.out.println("720p/playlist.m3u8:");
        System.out.print(videos.mediaPlaylist(video.id()).indent(2));

        System.out.println("\nAdaptive bitrate session (player decides before each segment):");
        double[][] networkTrace = { // {measured kbps, buffer seconds}
                {6000, 0}, {6000, 8}, {6000, 16}, {6000, 20}, {1500, 22}, {700, 12}, {900, 4}, {7000, 18}};
        Rendition current = ladder.get(0); // start low for a fast first frame
        for (double[] sample : networkTrace) {
            Rendition next = AdaptiveBitrateSelector.choose(ladder, current, sample[0], sample[1]);
            String line = "  bandwidth %4.0f kbps, buffer %2.0f s -> %-5s%s".formatted(sample[0], sample[1], next.name(),
                    explain(ladder, current, next));
            System.out.println(line.stripTrailing());
            current = next;
        }
    }

    private static String explain(List<Rendition> ladder, Rendition from, Rendition to) {
        int change = ladder.indexOf(to) - ladder.indexOf(from);
        return change > 0 ? "  ↑ up one level" : change < 0 ? "  ↓ down" : "";
    }
}
