package com.sdprep.fooddelivery;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks couriers and assigns the nearest free one to a pickup point.
 * Production: a geo index (see module 11), batching of nearby orders, and acceptance offers to couriers.
 */
public class DispatchService {

    private record CourierState(Location location, boolean busy) {
    }

    private final Map<String, CourierState> couriers = new LinkedHashMap<>();

    public synchronized void courierOnline(String courierId, Location location) {
        couriers.put(courierId, new CourierState(location, false));
    }

    /** Atomically picks and reserves the nearest free courier (synchronized: no double assignment). */
    public synchronized Optional<String> assignNearest(Location pickup) {
        Optional<String> nearest = couriers.entrySet().stream()
                .filter(entry -> !entry.getValue().busy())
                .min(Comparator.comparingDouble(entry -> entry.getValue().location().distanceKmTo(pickup)))
                .map(Map.Entry::getKey);
        nearest.ifPresent(courierId -> couriers.put(courierId, new CourierState(couriers.get(courierId).location(), true)));
        return nearest;
    }

    /** The courier is free again at {@code location} (where they finished or abandoned the job). */
    public synchronized void release(String courierId, Location location) {
        couriers.put(courierId, new CourierState(location, false));
    }

    public synchronized boolean isBusy(String courierId) {
        return couriers.get(courierId).busy();
    }

    public synchronized Location locationOf(String courierId) {
        return couriers.get(courierId).location();
    }
}
