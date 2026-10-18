package com.example.carrental.controller;

import java.util.List;

/**
 * Wire format. Deliberately plain strings and numbers (ISO-8601 for dates), so
 * the JSON does not depend on how the active Jackson version renders java.time.
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    /** Body for {@code POST /api/reservations/nl}. */
    public record NaturalLanguageRequest(String text, String customer) {
    }

    /** Body for the structured {@code POST /api/reservations}. {@code start} is ISO-8601, e.g. 2026-10-15T10:00. */
    public record BookingRequest(String carType, String start, Integer days, String customer) {
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
