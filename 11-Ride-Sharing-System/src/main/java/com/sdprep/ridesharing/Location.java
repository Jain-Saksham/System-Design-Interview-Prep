package com.sdprep.ridesharing;

/** A point on Earth in degrees. */
public record Location(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6_371;

    /** Great-circle distance (haversine formula). Accurate enough for city-scale matching. */
    public double distanceKmTo(Location other) {
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLng = Math.toRadians(other.longitude - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
