package com.sdprep.ticketbooking;

import java.util.List;

/** A confirmed, paid booking. Permanent. */
public record Booking(String bookingId, String showId, String userId, List<String> seats, long totalCents) {
}
