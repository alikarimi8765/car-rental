package com.example.carrental.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AvailabilityTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 15, 10, 0);

    @Test
    void peakConcurrencyMattersNotOverlapCount() {
        // Three sequential bookings: a new [T0, T0+3) overlaps all three, but peak
        // existing load is 1, so with capacity 2 there is still room.
        Reservation a = reservation("a", CarType.SUV, T0, 1);
        Reservation b = reservation("b", CarType.SUV, T0.plusDays(1), 1);
        Reservation c = reservation("c", CarType.SUV, T0.plusDays(2), 1);

        assertEquals(1, Availability.free(2, List.of(a, b, c), T0, T0.plusDays(3)));
    }

    @Test
    void backToBackHalfOpenIntervalsDoNotOverlap() {
        Reservation held = reservation("held", CarType.SEDAN, T0, 3);
        LocalDateTime nextStart = held.end();

        assertEquals(1, Availability.free(1, List.of(held), nextStart, nextStart.plusDays(2)));
        assertEquals(Optional.of(nextStart), Availability.earliestStart(1, List.of(held), T0, 2));
    }

    @Test
    void earliestStartEmptyWhenCapacityIsZero() {
        assertEquals(Optional.empty(), Availability.earliestStart(0, List.of(), T0, 2));
    }

    @Test
    void freeIsZeroWhenFleetIsFullForWindow() {
        List<Reservation> full = List.of(
                reservation("1", CarType.SUV, T0, 3),
                reservation("2", CarType.SUV, T0, 3),
                reservation("3", CarType.SUV, T0, 3));
        assertEquals(0, Availability.free(3, full, T0, T0.plusDays(3)));
    }

    @Test
    void nestedAndPartialOverlapsUsePeakNotBookingCount() {
        // Long booking covering the whole window, plus a short nested one.
        // Peak existing load is 2; with capacity 3 there is still room for a third.
        Reservation longHold = reservation("long", CarType.SUV, T0, 5);
        Reservation nested = reservation("nested", CarType.SUV, T0.plusDays(1), 1);

        assertEquals(1, Availability.free(3, List.of(longHold, nested), T0, T0.plusDays(3)));
        assertEquals(0, Availability.free(2, List.of(longHold, nested), T0, T0.plusDays(3)));
    }

    @Test
    void earliestStartSkipsSaturatedWindowToNextRelease() {
        // Capacity 1: held for 3 days from T0. A 2-day rental must wait until the hold ends.
        Reservation held = reservation("held", CarType.VAN, T0, 3);

        assertEquals(Optional.of(held.end()), Availability.earliestStart(1, List.of(held), T0, 2));
        assertEquals(0, Availability.free(1, List.of(held), T0, T0.plusDays(2)));
    }

    private static Reservation reservation(String id, CarType type, LocalDateTime start, int days) {
        return new Reservation(id, type, start, days, null);
    }
}
