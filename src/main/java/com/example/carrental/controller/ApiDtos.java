package com.example.carrental.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Wire format. Deliberately plain strings and numbers (ISO-8601 for dates), so
 * the JSON does not depend on how the active Jackson version renders java.time.
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    /** Maximum length of the free-text {@code customer} label (matches the database column). */
    public static final int MAX_CUSTOMER_LENGTH = 100;

    /** Maximum length of a natural-language request. */
    public static final int MAX_TEXT_LENGTH = 500;

    /**
     * Body for {@code POST /api/reservations/nl}. Bean Validation checks only the shape (present,
     * bounded); whether the text is a usable request is the parser's decision and is reported
     * as a clarification, not as a 400.
     */
    public record NaturalLanguageRequest(
            @NotNull(message = "text is required") @Size(max = MAX_TEXT_LENGTH, message = "text must be at most "
                    + MAX_TEXT_LENGTH + " characters") String text,
            @Size(max = MAX_CUSTOMER_LENGTH, message = "customer must be at most "
                    + MAX_CUSTOMER_LENGTH + " characters") String customer) {
    }

    /** Body for the structured {@code POST /api/reservations}. {@code start} is ISO-8601, e.g. 2026-10-15T10:00. */
    public record BookingRequest(
            @Size(max = 20, message = "carType must be at most 20 characters") String carType,
            @Size(max = 40, message = "start must be at most 40 characters") String start,
            Integer days,
            @Size(max = MAX_CUSTOMER_LENGTH, message = "customer must be at most "
                    + MAX_CUSTOMER_LENGTH + " characters") String customer) {
    }

    public record IssueDto(String kind, String field, String message) {
    }

    public record ParsedDto(
            String carType,
            String start,
            String startDate,
            String startTime,
            Integer durationDays,
            boolean complete,
            List<IssueDto> issues) {
    }

    public record ReservationDto(String id, String carType, String start, String end, int days, String customer) {
    }

    /** Result of a natural-language request; {@code reservation} is null unless outcome is RESERVED. */
    public record OutcomeDto(String outcome, String message, ParsedDto parsed, ReservationDto reservation) {
    }
}
