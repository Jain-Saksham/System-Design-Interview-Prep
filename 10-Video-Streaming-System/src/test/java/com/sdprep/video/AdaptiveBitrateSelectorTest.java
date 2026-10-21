package com.sdprep.video;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveBitrateSelectorTest {

    private static final List<Rendition> LADDER = Rendition.STANDARD_LADDER;
    private static final Rendition P240 = LADDER.get(0);
    private static final Rendition P360 = LADDER.get(1);
    private static final Rendition P720 = LADDER.get(2);
    private static final Rendition P1080 = LADDER.get(3);

    @Test
    void upgradesOnlyOneLevelAtATimeWithAComfortableBuffer() {
        assertEquals(P360, AdaptiveBitrateSelector.choose(LADDER, P240, 10_000, 20));
    }

    @Test
    void doesNotUpgradeWhileBufferIsStillBuilding() {
        assertEquals(P240, AdaptiveBitrateSelector.choose(LADDER, P240, 10_000, 10));
    }

    @Test
    void downgradesImmediatelyWhenBandwidthDrops() {
        // 1500 × 0.8 = 1200 kbps budget → 360p (800) is the best affordable rendition.
        assertEquals(P360, AdaptiveBitrateSelector.choose(LADDER, P1080, 1_500, 25));
    }

    @Test
    void lowBufferForcesAStepDownEvenWithGoodBandwidth() {
        assertEquals(P360, AdaptiveBitrateSelector.choose(LADDER, P720, 10_000, 3));
    }

    @Test
    void neverGoesBelowTheLowestRendition() {
        assertEquals(P240, AdaptiveBitrateSelector.choose(LADDER, P240, 50, 1));
    }

    @Test
    void keepsCurrentRenditionWhenItIsStillAffordable() {
        assertEquals(P720, AdaptiveBitrateSelector.choose(LADDER, P720, 4_000, 20)); // 3200 budget: 720p fits, 1080p doesn't
    }
}
