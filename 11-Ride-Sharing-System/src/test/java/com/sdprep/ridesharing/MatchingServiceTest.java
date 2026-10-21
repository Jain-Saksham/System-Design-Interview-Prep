package com.sdprep.ridesharing;

import com.sdprep.ridesharing.MatchingService.DriverStatus;
import com.sdprep.ridesharing.MatchingService.NoDriversAvailableException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchingServiceTest {

    private static final Location PICKUP = new Location(12.9750, 77.6060);
    private static final Location DROPOFF = new Location(12.9352, 77.6245);

    private final MatchingService rides = new MatchingService(new GridIndex(0.01), (driver, trip) -> true);

    @Test
    void assignsTheNearestAvailableDriver() {
        rides.goOnline("far", new Location(12.9900, 77.6200));
        rides.goOnline("near", new Location(12.9755, 77.6065));

        Trip trip = rides.requestRide("rider", PICKUP, DROPOFF);

        assertEquals("near", trip.driverId());
        assertEquals(DriverStatus.ON_TRIP, rides.statusOf("near"));
    }

    @Test
    void declinedOfferGoesToTheNextDriver() {
        MatchingService picky = new MatchingService(new GridIndex(0.01), (driver, trip) -> !driver.equals("near"));
        picky.goOnline("near", new Location(12.9755, 77.6065));
        picky.goOnline("next", new Location(12.9790, 77.6100));

        Trip trip = picky.requestRide("rider", PICKUP, DROPOFF);

        assertEquals("next", trip.driverId());
        assertEquals(DriverStatus.AVAILABLE, picky.statusOf("near")); // released after declining
    }

    @Test
    void wideningTheSearchDoesNotReOfferToDriversWhoDeclined() {
        List<String> offers = new ArrayList<>();
        MatchingService picky = new MatchingService(new GridIndex(0.01), (driver, trip) -> {
            offers.add(driver);
            return driver.equals("distant");
        });
        picky.goOnline("near", new Location(12.9755, 77.6065));      // declines
        picky.goOnline("distant", new Location(13.0050, 77.6060));   // ~3.3 km, accepts

        picky.requestRide("rider", PICKUP, DROPOFF);

        assertEquals(List.of("near", "distant"), offers); // "near" asked once, not again in the 5 km pass
    }

    @Test
    void searchWidensWhenNoDriverIsClose() {
        rides.goOnline("distant", new Location(13.0050, 77.6060)); // ~3.3 km: outside 2 km, inside 5 km

        assertEquals("distant", rides.requestRide("rider", PICKUP, DROPOFF).driverId());
    }

    @Test
    void failsWhenNobodyIsWithinTheMaximumRadius() {
        rides.goOnline("too-far", new Location(13.1000, 77.6060)); // ~14 km

        assertThrows(NoDriversAvailableException.class, () -> rides.requestRide("rider", PICKUP, DROPOFF));
    }

    @Test
    void offlineAndBusyDriversAreSkipped() {
        rides.goOnline("busy", new Location(12.9755, 77.6065));
        rides.requestRide("first-rider", PICKUP, DROPOFF);
        rides.goOnline("offline", new Location(12.9751, 77.6061));
        rides.goOffline("offline");

        assertThrows(NoDriversAvailableException.class, () -> rides.requestRide("second-rider", PICKUP, DROPOFF));
    }

    @Test
    void completedTripFreesTheDriverAtTheDropoff() {
        rides.goOnline("d1", new Location(12.9755, 77.6065));
        Trip trip = rides.requestRide("rider", PICKUP, DROPOFF);

        rides.startTrip(trip.id());
        rides.completeTrip(trip.id());

        assertEquals(Trip.Status.COMPLETED, trip.status());
        assertEquals(DriverStatus.AVAILABLE, rides.statusOf("d1"));
        assertEquals("d1", rides.requestRide("next-rider", DROPOFF, PICKUP).driverId());
    }

    @Test
    void tripStepsMustHappenInOrder() {
        rides.goOnline("d1", new Location(12.9755, 77.6065));
        Trip trip = rides.requestRide("rider", PICKUP, DROPOFF);

        assertThrows(IllegalStateException.class, () -> rides.completeTrip(trip.id())); // not started yet
    }

    @Test
    void concurrentRequestsNeverShareADriver() throws InterruptedException {
        IntStream.range(0, 10).forEach(i -> rides.goOnline("d" + i, new Location(12.9750 + i * 0.0005, 77.6060)));
        Set<String> assignedDrivers = ConcurrentHashMap.newKeySet();
        Set<String> tripIds = ConcurrentHashMap.newKeySet();

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 50; i++) {
                String rider = "r" + i;
                pool.submit(() -> {
                    try {
                        Trip trip = rides.requestRide(rider, PICKUP, DROPOFF);
                        assignedDrivers.add(trip.driverId());
                        tripIds.add(trip.id());
                    } catch (NoDriversAvailableException expected) {
                        // 50 riders, 10 drivers: most riders get nothing
                    }
                });
            }
        }

        assertEquals(10, tripIds.size());
        assertEquals(10, assignedDrivers.size()); // 10 trips, 10 different drivers
    }
}
