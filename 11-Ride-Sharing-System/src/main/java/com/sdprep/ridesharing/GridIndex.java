package com.sdprep.ridesharing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds drivers near a point without scanning every driver. The map is divided into square cells
 * (~1.1 km at 0.01°); each cell lists the drivers inside it. A search only looks at the cells
 * overlapping the search radius.
 *
 * <p>Production: Redis GEO (geohash) or Uber's H3 hexagons. Same idea: bucket locations by area.
 */
public class GridIndex {

    public record NearbyDriver(String driverId, double distanceKm) {
    }

    private record Cell(long x, long y) {
    }

    private static final double KM_PER_DEGREE_LATITUDE = 111.0;

    private final double cellSizeDegrees;
    private final Map<Cell, Set<String>> driversByCell = new ConcurrentHashMap<>();
    private final Map<String, Location> locationsByDriver = new ConcurrentHashMap<>();

    public GridIndex(double cellSizeDegrees) {
        this.cellSizeDegrees = cellSizeDegrees;
    }

    /** Called on every GPS ping (every few seconds per driver). Moves the driver between cells if needed. */
    public synchronized void update(String driverId, Location location) {
        Location previous = locationsByDriver.put(driverId, location);
        if (previous != null) {
            driversByCell.getOrDefault(cellOf(previous), Set.of()).remove(driverId);
        }
        driversByCell.computeIfAbsent(cellOf(location), cell -> ConcurrentHashMap.newKeySet()).add(driverId);
    }

    public synchronized void remove(String driverId) {
        Location previous = locationsByDriver.remove(driverId);
        if (previous != null) {
            driversByCell.getOrDefault(cellOf(previous), Set.of()).remove(driverId);
        }
    }

    public Location locationOf(String driverId) {
        return locationsByDriver.get(driverId);
    }

    /** Drivers within {@code radiusKm}, nearest first. */
    public synchronized List<NearbyDriver> nearby(Location center, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE_LATITUDE;
        // A degree of longitude shrinks toward the poles.
        double lngDelta = radiusKm / (KM_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(center.latitude())));
        Cell min = cellOf(new Location(center.latitude() - latDelta, center.longitude() - lngDelta));
        Cell max = cellOf(new Location(center.latitude() + latDelta, center.longitude() + lngDelta));

        List<NearbyDriver> found = new ArrayList<>();
        for (long x = min.x(); x <= max.x(); x++) {
            for (long y = min.y(); y <= max.y(); y++) {
                for (String driverId : driversByCell.getOrDefault(new Cell(x, y), Set.of())) {
                    double distance = center.distanceKmTo(locationsByDriver.get(driverId));
                    if (distance <= radiusKm) { // the cell square covers more than the circle
                        found.add(new NearbyDriver(driverId, distance));
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(NearbyDriver::distanceKm));
        return found;
    }

    private Cell cellOf(Location location) {
        return new Cell((long) Math.floor(location.longitude() / cellSizeDegrees),
                (long) Math.floor(location.latitude() / cellSizeDegrees));
    }
}
