package com.sdprep.ticketbooking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Seat state for one show. Every method is synchronized on the show, so check-then-update is atomic:
 * two users can never hold the same seat. Locking per show (not globally) lets different shows proceed in parallel.
 *
 * <p>Production: one row per seat; a hold is
 * {@code UPDATE show_seats SET status='HELD', hold_id=?, hold_expires_at=? WHERE show_id=? AND seat_id IN (...)
 *  AND (status='AVAILABLE' OR (status='HELD' AND hold_expires_at < now()))} and must update every requested row.
 */
class ShowSeats {

    private final Set<String> allSeats;
    private final Map<String, SeatHold> holdsBySeat = new HashMap<>();
    private final Map<String, String> bookingIdsBySeat = new HashMap<>();

    ShowSeats(Set<String> allSeats) {
        this.allSeats = Set.copyOf(allSeats);
    }

    /** All-or-nothing: holds every seat, or none if any one is taken. Expired holds count as free. */
    synchronized void hold(SeatHold hold, long nowMillis) {
        Set<String> unavailable = new TreeSet<>();
        for (String seat : hold.seats()) {
            if (!allSeats.contains(seat) || !isFree(seat, nowMillis)) {
                unavailable.add(seat);
            }
        }
        if (!unavailable.isEmpty()) {
            throw new BookingExceptions.SeatsUnavailableException(unavailable);
        }
        hold.seats().forEach(seat -> holdsBySeat.put(seat, hold));
    }

    /** Converts a still-valid hold into a booking. */
    synchronized void book(SeatHold hold, String bookingId, long nowMillis) {
        boolean stillOurs = hold.seats().stream().allMatch(seat -> hold.equals(holdsBySeat.get(seat)));
        if (hold.isExpired(nowMillis) || !stillOurs) {
            throw new BookingExceptions.HoldExpiredException(hold.holdId());
        }
        hold.seats().forEach(seat -> {
            holdsBySeat.remove(seat);
            bookingIdsBySeat.put(seat, bookingId);
        });
    }

    synchronized void release(SeatHold hold) {
        hold.seats().forEach(seat -> holdsBySeat.remove(seat, hold)); // only if it's still this hold
    }

    /** Removes expired holds. Returns how many seats were freed. */
    synchronized int releaseExpired(long nowMillis) {
        int before = holdsBySeat.size();
        holdsBySeat.values().removeIf(hold -> hold.isExpired(nowMillis));
        return before - holdsBySeat.size();
    }

    synchronized List<String> availableSeats(long nowMillis) {
        return allSeats.stream().filter(seat -> isFree(seat, nowMillis)).sorted().toList();
    }

    private boolean isFree(String seat, long nowMillis) {
        if (bookingIdsBySeat.containsKey(seat)) {
            return false;
        }
        SeatHold hold = holdsBySeat.get(seat);
        return hold == null || hold.isExpired(nowMillis); // lazy expiry: no need to wait for the sweeper
    }
}
