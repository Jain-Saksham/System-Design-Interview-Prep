package com.sdprep.video;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoServiceTest {

    private SegmentStore segments;
    private TranscodingPipeline pipeline;
    private VideoService videos;

    @BeforeEach
    void setUp() {
        segments = new SegmentStore();
        pipeline = new TranscodingPipeline(segments, 6, Rendition.STANDARD_LADDER);
        videos = new VideoService(pipeline, 6, Rendition.STANDARD_LADDER);
    }

    @Test
    void createsOneJobPerSegmentPerRendition() {
        videos.upload("clip", 20); // ceil(20 / 6) = 4 segments

        assertEquals(16, pipeline.pendingJobs());
    }

    @Test
    void videoBecomesReadyOnlyAfterTheLastJob() {
        Video video = videos.upload("clip", 12); // 2 segments × 4 renditions = 8 jobs

        for (int i = 0; i < 7; i++) {
            pipeline.processNext();
        }
        assertEquals(Video.Status.PROCESSING, video.status());

        pipeline.processNext();
        assertEquals(Video.Status.READY, video.status());
    }

    @Test
    void storesEverySegmentOfEveryRendition() {
        Video video = videos.upload("clip", 12);
        pipeline.processAll();

        assertEquals(8, segments.segmentCount());
        assertTrue(segments.contains("videos/" + video.id() + "/1080p/seg_1.ts"));
    }

    @Test
    void segmentSizeMatchesBitrateTimesDuration() {
        SegmentStore store = new SegmentStore();
        TranscodingPipeline singleRendition = new TranscodingPipeline(store, 6, Rendition.STANDARD_LADDER.subList(1, 2));
        singleRendition.submit(new Video("v", "t", 6));
        singleRendition.processAll();

        assertEquals(800 * 1000 / 8 * 6, store.totalBytes()); // 800 kbps for 6 s = 600 KB
    }

    @Test
    void playlistsAreUnavailableWhileProcessing() {
        Video video = videos.upload("clip", 20);

        assertThrows(VideoService.NotReadyException.class, () -> videos.masterPlaylist(video.id()));
    }

    @Test
    void rejectsInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () -> videos.upload("bad", 0));
    }
}
