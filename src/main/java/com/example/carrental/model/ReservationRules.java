package com.example.carrental.model;

import com.example.carrental.exception.InvalidReservationException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Business rules for accepting a reservation request.
 */
public record ReservationRules(Clock clock, int maxRentalDays) {

    public static final int DEFAULT_MAX_DAYS = 30;

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ReservationRules {
        clock = Objects.requireNonNull(clock, "clock");
        if (maxRentalDays < 1) {
            throw new IllegalArgumentException("maxRentalDays must be at least 1");
        }
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public void check(CarType type, LocalDateTime start, int days) {
        if (type == null) {
            throw new InvalidReservationException("Car type is required.");
        }
        if (start == null) {
            throw new InvalidReservationException("Start time is required.");
        }
        if (days < 1 || days > maxRentalDays) {
            throw new InvalidReservationException(
                "Rental duration must be between 1 and %d days (got %d).".formatted(maxRentalDays, days));
        }
        if (start.isBefore(now())) {
            throw new InvalidReservationException(
                "Start time %s is in the past.".formatted(FORMAT.format(start)));
        }
    }
}
