package com.sdprep.ticketbooking;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Two-step booking, as on BookMyShow or Ticketmaster:
 * <ol>
 *   <li><b>Hold</b> seats for a few minutes while the user pays. Nobody else can take them meanwhile.</li>
 *   <li><b>Confirm</b> after payment, turning the hold into a permanent booking.</li>
 * </ol>
 * Unconfirmed holds expire on their own, so abandoned checkouts don't lock seats forever.
 */
public class BookingService {

    static final int MAX_SEATS_PER_HOLD = 10;

    private final LongSupplier clockMillis;
    private final long holdMillis;
    private final long pricePerSeatCents;
    private final Map<String, ShowSeats> showsById = new ConcurrentHashMap<>();
    private final Map<String, SeatHold> holdsById = new ConcurrentHashMap<>();
    private final Map<String, Booking> bookingsByHoldId = new ConcurrentHashMap<>();
    private final AtomicLong nextHoldId = new AtomicLong(1);
    private final AtomicLong nextBookingId = new AtomicLong(1);

    public BookingService(LongSupplier clockMillis, Duration holdDuration, long pricePerSeatCents) {
        this.clockMillis = clockMillis;
        this.holdMillis = holdDuration.toMillis();
        this.pricePerSeatCents = pricePerSeatCents;
    }

    public void addShow(String showId, Set<String> seats) {
        showsById.put(showId, new ShowSeats(seats));
    }

    public SeatHold holdSeats(String userId, String showId, Set<String> seats) {
        if (seats.isEmpty() || seats.size() > MAX_SEATS_PER_HOLD) {
            throw new IllegalArgumentException("Hold between 1 and " + MAX_SEATS_PER_HOLD + " seats");
        }
        long now = clockMillis.getAsLong();
        SeatHold hold = new SeatHold("hold-" + nextHoldId.getAndIncrement(), showId, userId, Set.copyOf(seats), now + holdMillis);
        show(showId).hold(hold, now); // throws SeatsUnavailableException if any seat is taken
        holdsById.put(hold.holdId(), hold);
        return hold;
    }

    /**
     * Call after payment succeeds. Confirming the same hold twice returns the same booking (safe to retry).
     * Production: if the hold expired after the customer paid, refund automatically.
     */
    public Booking confirm(String holdId) {
        Booking existing = bookingsByHoldId.get(holdId);
        if (existing != null) {
            return existing;
        }
        SeatHold hold = holdsById.get(holdId);
        if (hold == null) {
            throw new IllegalArgumentException("Unknown hold: " + holdId);
        }
        long now = clockMillis.getAsLong();
        if (hold.isExpired(now)) {
            throw new BookingExceptions.HoldExpiredException(holdId); // fail fast, before allocating a booking ID
        }
        String bookingId = "book-" + nextBookingId.getAndIncrement();
        show(hold.showId()).book(hold, bookingId, now); // re-checks under the show's lock
        Booking booking = new Booking(bookingId, hold.showId(), hold.userId(),
                hold.seats().stream().sorted().toList(), hold.seats().size() * pricePerSeatCents);
        bookingsByHoldId.put(holdId, booking);
        holdsById.remove(holdId);
        return booking;
    }

    /** The user backed out: free the seats immediately instead of waiting for expiry. */
    public void cancelHold(String holdId) {
        SeatHold hold = holdsById.remove(holdId);
        if (hold != null) {
            show(hold.showId()).release(hold);
        }
    }

    /**
     * Background sweeper. Expired holds are already treated as free at read time;
     * this just cleans up memory. Production: Redis key TTLs or a scheduled job.
     */
    public int releaseExpiredHolds() {
        long now = clockMillis.getAsLong();
        holdsById.values().removeIf(hold -> hold.isExpired(now));
        return showsById.values().stream().mapToInt(show -> show.releaseExpired(now)).sum();
    }

    public List<String> availableSeats(String showId) {
        return show(showId).availableSeats(clockMillis.getAsLong());
    }

    private ShowSeats show(String showId) {
        ShowSeats show = showsById.get(showId);
        if (show == null) {
            throw new IllegalArgumentException("Unknown show: " + showId);
        }
        return show;
    }
}
