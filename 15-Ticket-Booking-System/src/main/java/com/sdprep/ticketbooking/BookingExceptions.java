package com.sdprep.ticketbooking;

import java.util.Set;

/** Errors a booking client must handle. */
public final class BookingExceptions {

    private BookingExceptions() {
    }

    /** Someone else holds or has booked at least one requested seat → HTTP 409. */
    public static class SeatsUnavailableException extends RuntimeException {
        public SeatsUnavailableException(Set<String> seats) {
            super("Seats not available: " + seats);
        }
    }

    /** The hold timed out before confirmation; the seats may already belong to someone else → HTTP 410. */
    public static class HoldExpiredException extends RuntimeException {
        public HoldExpiredException(String holdId) {
            super("Hold " + holdId + " has expired");
        }
    }
}
