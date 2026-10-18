package com.example.carrental.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 15, 10, 0);

    @Test
    void endIsStartPlusDays() {
        Reservation r = new Reservation("id", CarType.SUV, T0, 3, null);
        assertEquals(T0.plusDays(3), r.end());
    }

    @Test
    void halfOpenIntervalIsActiveAtStartButNotAtEnd() {
        Reservation r = new Reservation("id", CarType.VAN, T0, 2, null);
        assertTrue(r.isActiveAt(T0));
        assertTrue(r.isActiveAt(T0.plusDays(1)));
        assertFalse(r.isActiveAt(T0.plusDays(2)));
        assertFalse(r.isActiveAt(T0.minusMinutes(1)));
    }

    @Test
    void overlapsUsesHalfOpenSemantics() {
        Reservation held = new Reservation("held", CarType.SEDAN, T0, 3, null);

        assertTrue(held.overlaps(T0, T0.plusDays(1)));
        assertTrue(held.overlaps(T0.plusDays(1), T0.plusDays(4)));
        assertFalse(held.overlaps(held.end(), held.end().plusDays(2)));
        assertFalse(held.overlaps(T0.minusDays(2), T0));
    }

    @Test
    void rejectsNonPositiveDays() {
        assertThrows(IllegalArgumentException.class,
                () -> new Reservation("id", CarType.SUV, T0, 0, null));
    }
}
