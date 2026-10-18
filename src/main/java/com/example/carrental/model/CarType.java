package com.example.carrental.model;

import com.example.carrental.exception.InvalidReservationException;

import java.util.Arrays;

public enum CarType {
    SEDAN("sedan"),
    SUV("SUV"),
    VAN("van");

    private final String label;

    CarType(String label) {
        this.label = label;
    }

    /** Human-readable name ("SUV", "sedan", "van"). */
    public String label() {
        return label;
    }

    /** Lenient lookup for API input: " suv ", "SUV" and "Suv" all work. */
    public static CarType parse(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidReservationException("Car type is required (sedan, SUV or van).");
        }
        String wanted = text.strip();
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new InvalidReservationException(
                        "Unknown car type '%s'. Supported types: sedan, SUV, van.".formatted(wanted)));
    }
}
