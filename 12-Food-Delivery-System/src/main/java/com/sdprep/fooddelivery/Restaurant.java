package com.sdprep.fooddelivery;

/** @param prepMinutes typical time to prepare an order (production: predicted per order by an ML model) */
public record Restaurant(String id, String name, Location location, int prepMinutes) {
}
