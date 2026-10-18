package com.example.carrental.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.carrental.TestClocks;
import com.example.carrental.exception.InvalidReservationException;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ReservationRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 17, 49);
    private final ReservationRules rules = new ReservationRules(TestClocks.fixed(NOW), 30);

    @Test
    void acceptsValidRequest() {
        assertDoesNotThrow(() -> rules.check(CarType.SUV, NOW.plusDays(1), 3));
    }

    @Test
    void rejectsNullTypeAndStart() {
        assertThrows(InvalidReservationException.class, () -> rules.check(null, NOW.plusDays(1), 3));
        assertThrows(InvalidReservationException.class, () -> rules.check(CarType.SUV, null, 3));
    }

    @Test
    void rejectsDaysOutsideRange() {
        assertThrows(InvalidReservationException.class, () -> rules.check(CarType.SUV, NOW.plusDays(1), 0));
        assertThrows(InvalidReservationException.class, () -> rules.check(CarType.SUV, NOW.plusDays(1), 31));
    }

    @Test
    void rejectsStartInThePast() {
        assertThrows(InvalidReservationException.class, () -> rules.check(CarType.SUV, NOW.minusMinutes(1), 3));
    }

    @Test
    void allowsStartExactlyNow() {
        assertDoesNotThrow(() -> rules.check(CarType.VAN, NOW, 1));
    }
}
