package com.sdprep.video;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HlsManifestsTest {

    @Test
    void masterPlaylistListsEveryRenditionWithBandwidth() {
        String master = HlsManifests.masterPlaylist(Rendition.STANDARD_LADDER);

        assertTrue(master.startsWith("#EXTM3U\n"));
        assertTrue(master.contains("#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720\n720p/playlist.m3u8\n"));
        assertEquals(4, master.lines().filter(line -> line.startsWith("#EXT-X-STREAM-INF")).count());
    }

    @Test
    void mediaPlaylistListsSegmentsWithAShorterLastSegment() {
        String media = HlsManifests.mediaPlaylist(new Video("v1", "clip", 20), 6);

        List<String> durations = media.lines().filter(line -> line.startsWith("#EXTINF")).toList();
        assertEquals(List.of("#EXTINF:6.000,", "#EXTINF:6.000,", "#EXTINF:6.000,", "#EXTINF:2.000,"), durations);
        assertTrue(media.contains("#EXT-X-TARGETDURATION:6\n"));
        assertTrue(media.contains("seg_3.ts\n"));
        assertTrue(media.endsWith("#EXT-X-ENDLIST\n"));
    }
}
