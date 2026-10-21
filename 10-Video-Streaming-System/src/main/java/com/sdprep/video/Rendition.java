package com.sdprep.video;

import java.util.List;

/** One quality level a video is transcoded into. */
public record Rendition(String name, int width, int height, int bitrateKbps) {

    /** A typical bitrate ladder, lowest to highest. */
    public static final List<Rendition> STANDARD_LADDER = List.of(
            new Rendition("240p", 426, 240, 400),
            new Rendition("360p", 640, 360, 800),
            new Rendition("720p", 1280, 720, 2800),
            new Rendition("1080p", 1920, 1080, 5000));
}
