package com.sdprep.fooddelivery;

/** A point on a flat city map, in km. (Module 11 shows real lat/lng geospatial indexing.) */
public record Location(double xKm, double yKm) {

    public double distanceKmTo(Location other) {
        return Math.hypot(other.xKm - xKm, other.yKm - yKm);
    }
}
