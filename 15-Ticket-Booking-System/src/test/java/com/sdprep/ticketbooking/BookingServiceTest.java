package com.sdprep.ticketbooking;

import com.sdprep.ticketbooking.BookingExceptions.HoldExpiredException;
import com.sdprep.ticketbooking.BookingExceptions.SeatsUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingServiceTest {

    private static final long FIVE_MINUTES = Duration.ofMinutes(5).toMillis();

    private final AtomicLong clock = new AtomicLong();
    private BookingService booking;

    @BeforeEach
    void setUp() {
        booking = new BookingService(clock::get, Duration.ofMinutes(5), 10_000);
        booking.addShow("show", Set.of("A1", "A2", "A3", "A4"));
    }

    @Test
    void holdThenConfirmBooksTheSeats() {
        SeatHold hold = booking.holdSeats("alice", "show", Set.of("A1", "A2"));
        assertEquals(List.of("A3", "A4"), booking.availableSeats("show"));

        Booking confirmed = booking.confirm(hold.holdId());

        assertEquals(List.of("A1", "A2"), confirmed.seats());
        assertEquals(20_000, confirmed.totalCents());
        assertEquals(List.of("A3", "A4"), booking.availableSeats("show"));
    }

    @Test
    void overlappingHoldIsRejectedAndHoldsNothing() {
        booking.holdSeats("alice", "show", Set.of("A2"));

        SeatsUnavailableException e = assertThrows(SeatsUnavailableException.class,
                () -> booking.holdSeats("bob", "show", Set.of("A2", "A3")));

        assertEquals("Seats not available: [A2]", e.getMessage());
        assertEquals(List.of("A1", "A3", "A4"), booking.availableSeats("show")); // A3 was not held for bob
    }

    @Test
    void manyUsersRacingForOneSeatProduceExactlyOneHold() throws InterruptedException {
        Set<String> winners = ConcurrentHashMap.newKeySet();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 200; i++) {
                String user = "user" + i;
                pool.submit(() -> {
                    try {
                        booking.holdSeats(user, "show", Set.of("A1"));
                        winners.add(user);
                    } catch (SeatsUnavailableException expected) {
                        // lost the race
                    }
                });
            }
        }
        assertEquals(1, winners.size());
    }

    @Test
    void expiredHoldFreesSeatsForOthers() {
        booking.holdSeats("alice", "show", Set.of("A1"));

        clock.set(FIVE_MINUTES);

        assertEquals(List.of("A1", "A2", "A3", "A4"), booking.availableSeats("show"));
        booking.holdSeats("bob", "show", Set.of("A1")); // no exception
    }

    @Test
    void confirmingAfterExpiryFails() {
        SeatHold hold = booking.holdSeats("alice", "show", Set.of("A1"));
        clock.set(FIVE_MINUTES + 1);

        assertThrows(HoldExpiredException.class, () -> booking.confirm(hold.holdId()));
    }

    @Test
    void confirmingAfterSomeoneElseTookTheExpiredSeatsFails() {
        SeatHold aliceHold = booking.holdSeats("alice", "show", Set.of("A1"));
        clock.set(FIVE_MINUTES);
        booking.holdSeats("bob", "show", Set.of("A1"));

        assertThrows(HoldExpiredException.class, () -> booking.confirm(aliceHold.holdId()));
    }

    @Test
    void confirmIsSafeToRetry() {
        SeatHold hold = booking.holdSeats("alice", "show", Set.of("A1"));

        assertEquals(booking.confirm(hold.holdId()), booking.confirm(hold.holdId()));
    }

    @Test
    void bookedSeatsCanNeverBeHeldAgain() {
        booking.confirm(booking.holdSeats("alice", "show", Set.of("A1")).holdId());
        clock.set(FIVE_MINUTES * 100);

        assertThrows(SeatsUnavailableException.class, () -> booking.holdSeats("bob", "show", Set.of("A1")));
    }

    @Test
    void cancellingAHoldFreesSeatsImmediately() {
        SeatHold hold = booking.holdSeats("alice", "show", Set.of("A1", "A2"));

        booking.cancelHold(hold.holdId());

        assertEquals(4, booking.availableSeats("show").size());
    }

    @Test
    void sweeperCleansUpExpiredHolds() {
        booking.holdSeats("alice", "show", Set.of("A1", "A2"));
        booking.holdSeats("bob", "show", Set.of("A3"));
        clock.set(FIVE_MINUTES);

        assertEquals(3, booking.releaseExpiredHolds());
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(SeatsUnavailableException.class, () -> booking.holdSeats("alice", "show", Set.of("Z9")));
        assertThrows(IllegalArgumentException.class, () -> booking.holdSeats("alice", "show", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> booking.holdSeats("alice", "no-show", Set.of("A1")));
    }
}
