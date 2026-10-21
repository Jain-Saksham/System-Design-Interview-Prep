package com.sdprep.video;

import java.util.List;

/**
 * The player-side decision made before each segment download: which rendition to fetch next.
 * Goals, in order: never stall (rebuffer), then maximize quality, then avoid flickering between levels.
 */
public final class AdaptiveBitrateSelector {

    static final double BANDWIDTH_SAFETY_FACTOR = 0.8; // measured throughput is noisy; leave headroom
    static final double LOW_BUFFER_SECONDS = 5;          // close to stalling: get safer
    static final double COMFORTABLE_BUFFER_SECONDS = 15; // enough cushion to try a higher quality

    private AdaptiveBitrateSelector() {
    }

    /**
     * @param ladder         renditions, lowest bitrate first
     * @param current        rendition of the last segment
     * @param measuredKbps   throughput measured while downloading recent segments
     * @param bufferSeconds  video downloaded but not yet played
     */
    public static Rendition choose(List<Rendition> ladder, Rendition current, double measuredKbps, double bufferSeconds) {
        int currentLevel = ladder.indexOf(current);
        int affordableLevel = highestAffordableLevel(ladder, measuredKbps);

        if (bufferSeconds < LOW_BUFFER_SECONDS) {
            // About to stall: step down at least one level, further if bandwidth demands it.
            return ladder.get(Math.min(affordableLevel, Math.max(currentLevel - 1, 0)));
        }
        if (affordableLevel > currentLevel) {
            // Upgrade one level at a time, and only with a healthy buffer, to avoid quality flicker.
            return bufferSeconds >= COMFORTABLE_BUFFER_SECONDS ? ladder.get(currentLevel + 1) : current;
        }
        return ladder.get(affordableLevel); // bandwidth dropped: downgrade right away
    }

    private static int highestAffordableLevel(List<Rendition> ladder, double measuredKbps) {
        double budgetKbps = measuredKbps * BANDWIDTH_SAFETY_FACTOR;
        int level = 0;
        for (int i = 0; i < ladder.size(); i++) {
            if (ladder.get(i).bitrateKbps() <= budgetKbps) {
                level = i;
            }
        }
        return level;
    }
}
