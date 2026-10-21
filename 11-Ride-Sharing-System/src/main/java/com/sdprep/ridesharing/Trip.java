package com.sdprep.ridesharing;

/** One ride. Status moves forward only: DRIVER_ASSIGNED → IN_PROGRESS → COMPLETED. */
public class Trip {

    public enum Status { DRIVER_ASSIGNED, IN_PROGRESS, COMPLETED }

    private final String id;
    private final String riderId;
    private final String driverId;
    private final Location pickup;
    private final Location dropoff;
    private final double estimatedFare;
    private Status status = Status.DRIVER_ASSIGNED;

    public Trip(String id, String riderId, String driverId, Location pickup, Location dropoff, double estimatedFare) {
        this.id = id;
        this.riderId = riderId;
        this.driverId = driverId;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.estimatedFare = estimatedFare;
    }

    public String id() { return id; }
    public String riderId() { return riderId; }
    public String driverId() { return driverId; }
    public Location pickup() { return pickup; }
    public Location dropoff() { return dropoff; }
    public double estimatedFare() { return estimatedFare; }
    public synchronized Status status() { return status; }

    synchronized void moveTo(Status expected, Status next) {
        if (status != expected) {
            throw new IllegalStateException("Trip " + id + " is " + status + ", expected " + expected);
        }
        status = next;
    }
}
