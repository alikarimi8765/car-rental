package com.example.carrental.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An immutable booking for one car of {@code carType} during {@code [start, end)}.
 *
 * <p>The interval is half-open: a car returned at 10:00 can be picked up by
 * someone else at exactly 10:00.
 */
public record Reservation(String id, CarType carType, LocalDateTime start, int days, String customer) {

    public Reservation {
        id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(carType, "carType");
        Objects.requireNonNull(start, "start");
        if (days < 1) {
            throw new IllegalArgumentException("days must be at least 1");
        }
    }

    public LocalDateTime end() {
        return start.plusDays(days);
    }

    /** True if this reservation shares any time with {@code [from, to)}. */
    public boolean overlaps(LocalDateTime from, LocalDateTime to) {
        return start.isBefore(to) && from.isBefore(end());
    }

    public boolean isActiveAt(LocalDateTime instant) {
        return !instant.isBefore(start) && instant.isBefore(end());
    }
}
