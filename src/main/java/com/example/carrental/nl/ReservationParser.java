package com.example.carrental.nl;

import static com.example.carrental.nl.IssueKind.MISSING;
import static com.example.carrental.nl.RequestField.REQUEST;

import com.example.carrental.model.CarType;
import com.example.carrental.nl.ParseSupport.Found;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Rule-based natural-language parser for reservation requests.
 *
 * <pre>
 * "Reserve an SUV from 15 October 2026 at 10:00 for 3 days"
 *     -> carType=SUV, start=2026-10-15T10:00, durationDays=3
 * </pre>
 *
 * <p>Philosophy: never guess silently. Every field is either extracted
 * unambiguously or reported as an {@link Issue}:
 * <ul>
 *   <li>{@link IssueKind#MISSING} - nothing usable was found</li>
 *   <li>{@link IssueKind#AMBIGUOUS} - several readings are plausible ("at 10" -> 10:00 or 22:00)</li>
 *   <li>{@link IssueKind#UNSUPPORTED} - understood, but not supported ("truck", "5 hours", "2 SUVs")</li>
 *   <li>{@link IssueKind#INVALID} - understood, but impossible ("31 February", "25:00")</li>
 * </ul>
 *
 * <p>Extraction is a pipeline of small passes. Each pass finds its matches and
 * <em>masks them out</em> of the text (replacing them with spaces) before the
 * next pass runs, so "15" in "15 October" can never be re-read as an hour and
 * "3" in "3 days" can never be re-read as a quantity. Order matters:
 * dates, durations, times, car type.
 */
@Component
public final class ReservationParser {

    private final DateExtractor dates;

    /** @param clock anchors relative dates such as "tomorrow" and "in 3 days" */
    public ReservationParser(Clock clock) {
        this.dates = new DateExtractor(Objects.requireNonNull(clock, "clock"));
    }

    public ParseResult parse(String text) {
        String original = text == null ? "" : text;
        if (original.isBlank()) {
            return new ParseResult(original, null, null, null, null,
                    List.of(new Issue(MISSING, REQUEST, "The request is empty.")));
        }

        Optional<ParseResult> refused = RequestGuard.refuse(original);
        if (refused.isPresent()) {
            return refused.get();
        }

        Found<LocalDate> date = dates.extract(original);
        Found<Integer> duration = DurationExtractor.extract(date.text());
        Found<LocalTime> time = TimeExtractor.extract(duration.text());
        Found<CarType> car = CarTypeExtractor.extract(time.text());

        List<Issue> issues = new ArrayList<>();
        issues.addAll(car.issues());
        issues.addAll(date.issues());
        issues.addAll(time.issues());
        issues.addAll(duration.issues());
        return new ParseResult(original, car.value(), date.value(), time.value(), duration.value(), issues);
    }
}
