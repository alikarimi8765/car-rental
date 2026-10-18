package com.example.carrental.exception;

import com.example.carrental.model.CarType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** The fleet has no free car of the requested type for the whole period. */
public final class CarUnavailableException extends RentalException {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CarType carType;
    private final LocalDateTime start;
    private final LocalDateTime end;
    private final int capacity;

    public CarUnavailableException(CarType carType, LocalDateTime start, LocalDateTime end, int capacity) {
        super("No %s available from %s to %s (fleet size: %d)."
                .formatted(carType.label(), FORMAT.format(start), FORMAT.format(end), capacity));
        this.carType = carType;
        this.start = start;
        this.end = end;
        this.capacity = capacity;
    }

    public CarType carType() {
        return carType;
    }

    public LocalDateTime start() {
        return start;
    }

    public LocalDateTime end() {
        return end;
    }

    public int capacity() {
        return capacity;
    }
}
