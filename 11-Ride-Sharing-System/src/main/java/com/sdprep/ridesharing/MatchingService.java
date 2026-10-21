package com.sdprep.ridesharing;

import com.sdprep.ridesharing.GridIndex.NearbyDriver;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

/**
 * Matches riders with nearby available drivers and tracks the trip lifecycle.
 *
 * <p>Matching: search nearby (expanding the radius if needed) → offer to the nearest driver →
 * if they decline, the next nearest. Reserving a driver is an atomic compare-and-set, so two
 * simultaneous requests can never get the same driver.
 */
public class MatchingService {

    public enum DriverStatus { OFFLINE, AVAILABLE, ON_TRIP }

    public static class NoDriversAvailableException extends RuntimeException {
        NoDriversAvailableException() {
            super("No drivers available nearby");
        }
    }

    private static final double[] SEARCH_RADII_KM = {2, 5}; // widen the search if nobody is close

    private final GridIndex index;
    private final BiPredicate<String, Trip> driverAcceptsOffer; // production: push offer, wait ~15 s for a reply
    private final Map<String, DriverStatus> statusByDriver = new ConcurrentHashMap<>();
    private final Map<String, Trip> tripsById = new ConcurrentHashMap<>();
    private final AtomicLong nextTripId = new AtomicLong(1);

    public MatchingService(GridIndex index, BiPredicate<String, Trip> driverAcceptsOffer) {
        this.index = index;
        this.driverAcceptsOffer = driverAcceptsOffer;
    }

    public void goOnline(String driverId, Location location) {
        index.update(driverId, location);
        statusByDriver.put(driverId, DriverStatus.AVAILABLE);
    }

    public void goOffline(String driverId) {
        statusByDriver.put(driverId, DriverStatus.OFFLINE);
        index.remove(driverId);
    }

    /** GPS ping from the driver app. */
    public void updateLocation(String driverId, Location location) {
        index.update(driverId, location);
    }

    public DriverStatus statusOf(String driverId) {
        return statusByDriver.getOrDefault(driverId, DriverStatus.OFFLINE);
    }

    public Trip requestRide(String riderId, Location pickup, Location dropoff) {
        Set<String> alreadyOffered = new HashSet<>(); // a wider search must not re-ask drivers who declined
        for (double radiusKm : SEARCH_RADII_KM) {
            for (NearbyDriver candidate : index.nearby(pickup, radiusKm)) {
                if (!alreadyOffered.add(candidate.driverId())) {
                    continue;
                }
                Optional<Trip> trip = tryOffer(candidate.driverId(), riderId, pickup, dropoff);
                if (trip.isPresent()) {
                    return trip.get();
                }
            }
        }
        throw new NoDriversAvailableException();
    }

    public void startTrip(String tripId) {
        trip(tripId).moveTo(Trip.Status.DRIVER_ASSIGNED, Trip.Status.IN_PROGRESS);
    }

    /** The driver is free again, at the drop-off point. */
    public void completeTrip(String tripId) {
        Trip trip = trip(tripId);
        trip.moveTo(Trip.Status.IN_PROGRESS, Trip.Status.COMPLETED);
        index.update(trip.driverId(), trip.dropoff());
        statusByDriver.put(trip.driverId(), DriverStatus.AVAILABLE);
    }

    private Optional<Trip> tryOffer(String driverId, String riderId, Location pickup, Location dropoff) {
        // Atomic: only one request can move this driver from AVAILABLE to ON_TRIP.
        if (!statusByDriver.replace(driverId, DriverStatus.AVAILABLE, DriverStatus.ON_TRIP)) {
            return Optional.empty(); // already taken by another rider, or offline
        }
        Trip trip = new Trip("t" + nextTripId.getAndIncrement(), riderId, driverId, pickup, dropoff,
                FareCalculator.estimate(pickup, dropoff));
        if (!driverAcceptsOffer.test(driverId, trip)) {
            statusByDriver.put(driverId, DriverStatus.AVAILABLE); // declined: release for other riders
            return Optional.empty();
        }
        tripsById.put(trip.id(), trip);
        return Optional.of(trip);
    }

    private Trip trip(String tripId) {
        Trip trip = tripsById.get(tripId);
        if (trip == null) {
            throw new IllegalArgumentException("Unknown trip: " + tripId);
        }
        return trip;
    }

    /** For the demo: nearby drivers regardless of status. */
    List<NearbyDriver> driversNear(Location location, double radiusKm) {
        return index.nearby(location, radiusKm);
    }
}
