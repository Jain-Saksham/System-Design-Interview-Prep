package com.sdprep.video;

/** An uploaded video. Playable only once every rendition of every segment is ready. */
public class Video {

    public enum Status { PROCESSING, READY }

    private final String id;
    private final String title;
    private final double durationSeconds;
    private volatile Status status = Status.PROCESSING;

    public Video(String id, String title, double durationSeconds) {
        this.id = id;
        this.title = title;
        this.durationSeconds = durationSeconds;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public double durationSeconds() {
        return durationSeconds;
    }

    public Status status() {
        return status;
    }

    void markReady() {
        status = Status.READY;
    }

    public int segmentCount(double segmentSeconds) {
        return (int) Math.ceil(durationSeconds / segmentSeconds);
    }

    /** All segments are full length except possibly the last one. */
    public double segmentDuration(int segmentIndex, double segmentSeconds) {
        return Math.min(segmentSeconds, durationSeconds - segmentIndex * segmentSeconds);
    }
}
