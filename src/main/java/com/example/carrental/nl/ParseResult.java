package com.example.carrental.nl;

import com.example.carrental.model.CarType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Structured data extracted from a free-text request. Fields that could not be
 * extracted are {@code null}; the reason is in {@link #issues()}. Partial
 * results are kept so callers can ask a precise follow-up question.
 */
public record ParseResult(
        String text,
        CarType carType,
        LocalDate startDate,
        LocalTime startTime,
        Integer durationDays,
        List<Issue> issues) {

    public ParseResult {
        issues = List.copyOf(issues);
    }

    /** Start date and time combined; empty unless both were found. */
    public Optional<LocalDateTime> start() {
        if (startDate == null || startTime == null) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.of(startDate, startTime));
    }

    /** True only when every field was found and nothing needs clarifying. */
    public boolean isComplete() {
        return issues.isEmpty() && carType != null && startDate != null
                && startTime != null && durationDays != null;
    }

    public List<Issue> issuesFor(RequestField field) {
        return issues.stream().filter(issue -> issue.field() == field).toList();
    }
}
