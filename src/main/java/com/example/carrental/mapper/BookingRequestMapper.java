package com.example.carrental.mapper;

import com.example.carrental.controller.ApiDtos.BookingRequest;
import com.example.carrental.exception.InvalidReservationException;
import com.example.carrental.model.CarType;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Validates and converts the structured HTTP request into domain input. */
@Component
public class BookingRequestMapper {

    static final String START_EXAMPLE = "2026-10-15T10:00";

    public BookingCommand toCommand(BookingRequest request) {
        if (request.days() == null) {
            throw new InvalidReservationException("Rental duration (days) is required.");
        }
        return new BookingCommand(
                CarType.parse(request.carType()),
                parseStart(request.start()),
                request.days(),
                request.customer());
    }

    private static LocalDateTime parseStart(String start) {
        if (start == null || start.isBlank()) {
            throw invalidStart("Start time is required");
        }
        try {
            return LocalDateTime.parse(start.strip());
        } catch (DateTimeParseException exception) {
            throw invalidStart("Start time '%s' is not ISO-8601".formatted(start));
        }
    }

    private static InvalidReservationException invalidStart(String message) {
        return new InvalidReservationException("%s (e.g. %s).".formatted(message, START_EXAMPLE));
    }
}
