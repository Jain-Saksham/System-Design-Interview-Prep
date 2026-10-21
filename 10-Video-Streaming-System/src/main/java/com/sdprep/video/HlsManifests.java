package com.sdprep.video;

import java.util.List;
import java.util.Locale;

/**
 * Generates HLS playlists (the .m3u8 files players download first).
 * <ul>
 *   <li>Master playlist: lists every rendition with its bandwidth, so the player can choose.</li>
 *   <li>Media playlist: lists one rendition's segments in order.</li>
 * </ul>
 */
public final class HlsManifests {

    private HlsManifests() {
    }

    public static String masterPlaylist(List<Rendition> ladder) {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (Rendition rendition : ladder) {
            playlist.append("#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d%n"
                            .formatted(rendition.bitrateKbps() * 1000, rendition.width(), rendition.height()))
                    .append(rendition.name()).append("/playlist.m3u8\n");
        }
        return playlist.toString();
    }

    public static String mediaPlaylist(Video video, double segmentSeconds) {
        StringBuilder playlist = new StringBuilder()
                .append("#EXTM3U\n")
                .append("#EXT-X-VERSION:3\n")
                .append("#EXT-X-TARGETDURATION:").append((int) Math.ceil(segmentSeconds)).append('\n')
                .append("#EXT-X-MEDIA-SEQUENCE:0\n")
                .append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        for (int segment = 0; segment < video.segmentCount(segmentSeconds); segment++) {
            playlist.append(String.format(Locale.ROOT, "#EXTINF:%.3f,%n", video.segmentDuration(segment, segmentSeconds)))
                    .append("seg_").append(segment).append(".ts\n");
        }
        return playlist.append("#EXT-X-ENDLIST\n").toString(); // ENDLIST: a finished video, not a live stream
    }
}
