package com.sdprep.ridesharing;

/**
 * Upfront fare estimate: base + per-km rate on the straight-line trip distance.
 * Production uses road distance and time from a routing engine, plus surge pricing (demand ÷ supply per area).
 */
public final class FareCalculator {

    static final double BASE_FARE = 50;
    static final double PER_KM = 12;

    private FareCalculator() {
    }

    public static double estimate(Location pickup, Location dropoff) {
        double fare = BASE_FARE + PER_KM * pickup.distanceKmTo(dropoff);
        return Math.round(fare * 100) / 100.0;
    }
}
