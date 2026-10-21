package com.sdprep.ticketbooking;

import java.util.Set;

/** Seats temporarily reserved for one user while they pay. Automatically worthless after {@code expiresAtMillis}. */
public record SeatHold(String holdId, String showId, String userId, Set<String> seats, long expiresAtMillis) {

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
