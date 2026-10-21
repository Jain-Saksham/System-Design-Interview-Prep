package com.sdprep.ridesharing;

import com.sdprep.ridesharing.GridIndex.NearbyDriver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridIndexTest {

    private static final Location CENTER = new Location(12.9750, 77.6060);

    private final GridIndex index = new GridIndex(0.01);

    @Test
    void haversineDistanceIsCorrect() {
        // One degree of latitude is about 111 km.
        assertEquals(111.2, new Location(12, 77).distanceKmTo(new Location(13, 77)), 0.1);
    }

    @Test
    void findsDriversWithinRadiusNearestFirst() {
        index.update("far", new Location(12.9900, 77.6200));   // ~2.2 km
        index.update("near", new Location(12.9755, 77.6065));  // ~0.08 km
        index.update("mid", new Location(12.9790, 77.6100));   // ~0.6 km

        assertEquals(List.of("near", "mid"), ids(index.nearby(CENTER, 1)));
        assertEquals(List.of("near", "mid", "far"), ids(index.nearby(CENTER, 3)));
    }

    @Test
    void findsDriversInNeighbouringCells() {
        // Just across a cell boundary from the center, but only ~50 m away.
        Location acrossBoundary = new Location(12.9800 + 0.0001, 77.6060);
        index.update("neighbour", acrossBoundary);

        assertEquals(List.of("neighbour"), ids(index.nearby(new Location(12.9798, 77.6060), 0.5)));
    }

    @Test
    void movingDriverIsFoundAtNewLocationOnly() {
        index.update("d1", new Location(12.9755, 77.6065));
        index.update("d1", new Location(13.0500, 77.7000)); // drove ~13 km away

        assertTrue(index.nearby(CENTER, 2).isEmpty());
        assertEquals(List.of("d1"), ids(index.nearby(new Location(13.0500, 77.7000), 1)));
    }

    private static List<String> ids(List<NearbyDriver> drivers) {
        return drivers.stream().map(NearbyDriver::driverId).toList();
    }
}
