package com.sdprep.ridesharing;

import java.util.Set;

/** Runnable walkthrough: nearby search, a declined offer, assignment, trip lifecycle. */
public class RideSharingDemo {

    public static void main(String[] args) {
        Set<String> decliningDrivers = Set.of("dev");
        MatchingService rides = new MatchingService(new GridIndex(0.01),
                (driverId, trip) -> {
                    boolean accepts = !decliningDrivers.contains(driverId);
                    System.out.printf("  offer %s to %-5s -> %s%n", trip.id(), driverId, accepts ? "accepted" : "declined");
                    return accepts;
                });

        // Bengaluru, around MG Road
        Location pickup = new Location(12.9750, 77.6060);
        Location dropoff = new Location(12.9352, 77.6245); // Koramangala
        rides.goOnline("dev", new Location(12.9755, 77.6065));   // ~80 m away
        rides.goOnline("asha", new Location(12.9790, 77.6100));  // ~0.6 km
        rides.goOnline("ravi", new Location(12.9900, 77.6200));  // ~2.2 km
        rides.goOnline("meera", new Location(13.0350, 77.5970)); // ~6.7 km, outside every search radius

        System.out.println("Drivers within 2 km of the pickup:");
        rides.driversNear(pickup, 2).forEach(driver ->
                System.out.printf("  %-5s %.2f km%n", driver.driverId(), driver.distanceKm()));

        System.out.println("\nRider priya requests a ride to Koramangala:");
        Trip trip = rides.requestRide("priya", pickup, dropoff);
        System.out.printf("  matched with %s, upfront fare ₹%.2f%n", trip.driverId(), trip.estimatedFare());
        System.out.println("  asha is now " + rides.statusOf("asha") + "; dev is " + rides.statusOf("dev"));

        rides.startTrip(trip.id());
        rides.completeTrip(trip.id());
        System.out.println("\nTrip " + trip.id() + " " + trip.status() + "; asha is " + rides.statusOf("asha")
                + " again, now at the drop-off point");
    }
}
