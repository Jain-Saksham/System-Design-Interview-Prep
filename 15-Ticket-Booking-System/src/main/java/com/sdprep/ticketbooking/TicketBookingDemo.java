package com.sdprep.ticketbooking;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Runnable walkthrough: holds, a conflicting request, confirmation, hold expiry, retry safety. */
public class TicketBookingDemo {

    private static final AtomicLong clock = new AtomicLong();

    public static void main(String[] args) {
        BookingService booking = new BookingService(clock::get, Duration.ofMinutes(5), 25_000);
        booking.addShow("dune-7pm", Set.of("A1", "A2", "A3", "A4", "A5"));
        System.out.println("Show dune-7pm, holds last 5 minutes. Available: " + booking.availableSeats("dune-7pm"));

        at(0);
        SeatHold alice = booking.holdSeats("alice", "dune-7pm", Set.of("A1", "A2"));
        System.out.println("t=0 min  alice holds A1, A2 (" + alice.holdId() + ")");

        at(1);
        try {
            booking.holdSeats("bob", "dune-7pm", Set.of("A2", "A3"));
        } catch (BookingExceptions.SeatsUnavailableException e) {
            System.out.println("t=1 min  bob asks for A2, A3 -> rejected: " + e.getMessage() + " (nothing held: all-or-nothing)");
        }
        SeatHold bob = booking.holdSeats("bob", "dune-7pm", Set.of("A3", "A4"));
        System.out.println("t=1 min  bob holds A3, A4 instead. Available: " + booking.availableSeats("dune-7pm"));

        at(5);
        Booking bobBooking = booking.confirm(bob.holdId()); // his hold is valid until t=6
        System.out.println("t=5 min  bob pays in time -> " + bobBooking.bookingId() + " " + bobBooking.seats());

        at(6);
        System.out.println("t=6 min  alice's hold expired (she never paid). Available: " + booking.availableSeats("dune-7pm"));
        try {
            booking.confirm(alice.holdId());
        } catch (BookingExceptions.HoldExpiredException e) {
            System.out.println("t=6 min  alice finally pays -> " + e.getMessage() + " (refund issued)");
        }
        SeatHold carol = booking.holdSeats("carol", "dune-7pm", Set.of("A1", "A2"));
        Booking carolBooking = booking.confirm(carol.holdId());
        System.out.printf("t=6 min  carol holds and pays for A1, A2 -> %s, ₹%d%n",
                carolBooking.bookingId(), carolBooking.totalCents() / 100);

        System.out.println("\nbob's app retries its confirmation -> " + booking.confirm(bob.holdId()).bookingId() + " (same booking)");
        System.out.println("Remaining seats: " + booking.availableSeats("dune-7pm"));
    }

    private static void at(long minutes) {
        clock.set(minutes * 60_000);
    }
}
