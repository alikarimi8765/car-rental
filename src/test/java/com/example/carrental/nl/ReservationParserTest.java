package com.example.carrental.nl;

import static com.example.carrental.nl.IssueKind.AMBIGUOUS;
import static com.example.carrental.nl.IssueKind.INVALID;
import static com.example.carrental.nl.IssueKind.MISSING;
import static com.example.carrental.nl.IssueKind.UNSUPPORTED;
import static com.example.carrental.nl.RequestField.CAR_TYPE;
import static com.example.carrental.nl.RequestField.DURATION;
import static com.example.carrental.nl.RequestField.REQUEST;
import static com.example.carrental.nl.RequestField.START_DATE;
import static com.example.carrental.nl.RequestField.START_TIME;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.carrental.TestClocks;
import com.example.carrental.model.CarType;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class ReservationParserTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 17, 49); // a Thursday

    private final ReservationParser parser = new ReservationParser(TestClocks.fixed(NOW));

    private record Problem(RequestField field, IssueKind kind) {
    }

    private ParseResult parse(String text) {
        return parser.parse(text);
    }

    private static Set<Problem> problems(ParseResult result) {
        return result.issues().stream()
                .map(issue -> new Problem(issue.field(), issue.kind()))
                .collect(Collectors.toSet());
    }

    private static Set<Problem> only(RequestField field, IssueKind kind) {
        return Set.of(new Problem(field, kind));
    }

    // --------------------------------------------------------------- happy path

    @Test
    void exampleFromTheBrief() {
        ParseResult r = parse("Reserve an SUV from 15 October 2026 at 10:00 for 3 days");
        assertTrue(r.isComplete());
        assertEquals(CarType.SUV, r.carType());
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 0), r.start().orElseThrow());
        assertEquals(3, r.durationDays());
        assertEquals(List.of(), r.issues());
    }

    @Test
    void equivalentPhrasingsGiveIdenticalResults() {
        List<String> phrasings = List.of(
                "Reserve an SUV from 15 October 2026 at 10:00 for 3 days",
                "I'd like to book a SUV on October 15th, 2026 at 10am for 3 days",
                "rent an suv 2026-10-15 10:00 for three days",
                "Need an SUV starting 15 Oct 2026 at 10:00 AM for a 3-day rental",
                "Reserve an SUV from 15/10/2026 at 10:00 for 3 days",
                "SUV, 15th of October 2026, 10:00, 3 days",
                "Reserve an SUV from October 15, 2026 at 10:00 for 3 days",
                "Reserve an SUV for 3 days starting 2026-10-15T10:00",
                "RESERVE AN SUV FROM 15 OCTOBER 2026 AT 10:00 FOR 3 DAYS");
        for (String text : phrasings) {
            ParseResult r = parse(text);
            assertEquals(List.of(), r.issues(), text);
            assertEquals(CarType.SUV, r.carType(), text);
            assertEquals(LocalDateTime.of(2026, 10, 15, 10, 0), r.start().orElseThrow(), text);
            assertEquals(3, r.durationDays(), text);
        }
    }

    @Test
    void carTypeVocabulary() {
        Map<String, CarType> cases = Map.of(
                "a sedan", CarType.SEDAN,
                "a saloon", CarType.SEDAN,
                "an SUV", CarType.SUV,
                "a sport utility vehicle", CarType.SUV,
                "a van", CarType.VAN,
                "a minivan", CarType.VAN,
                "a mini-van", CarType.VAN);
        cases.forEach((phrase, expected) -> {
            ParseResult r = parse("Reserve " + phrase + " on 2026-10-15 at 10:00 for 2 days");
            assertEquals(List.of(), r.issues(), phrase);
            assertEquals(expected, r.carType(), phrase);
        });
    }

    @Test
    void durations() {
        Map<String, Integer> cases = Map.of(
                "for 1 day", 1,
                "for a day", 1,
                "for one day", 1,
                "for 10 days", 10,
                "for a week", 7,
                "for two weeks", 14,
                "for twenty days", 20,
                "for 3-day", 3);
        cases.forEach((phrase, expected) -> {
            ParseResult r = parse("Reserve a van on 2026-10-15 at 10:00 " + phrase);
            assertEquals(List.of(), r.issues(), phrase);
            assertEquals(expected, r.durationDays(), phrase);
        });
    }

    @Test
    void times() {
        Map<String, LocalTime> cases = Map.ofEntries(
                entry("at 10:00", LocalTime.of(10, 0)),
                entry("at 10am", LocalTime.of(10, 0)),
                entry("at 10 a.m.", LocalTime.of(10, 0)),
                entry("at 3pm", LocalTime.of(15, 0)),
                entry("at 3:45 PM", LocalTime.of(15, 45)),
                entry("at 12:30 am", LocalTime.of(0, 30)),
                entry("at 12pm", LocalTime.of(12, 0)),
                entry("at 15:30", LocalTime.of(15, 30)),
                entry("at 22", LocalTime.of(22, 0)), // bare hour > 12 can only be 24h
                entry("at 10:00:00", LocalTime.of(10, 0)), // zero seconds are the same minute
                entry("at noon", LocalTime.NOON),
                entry("at midnight", LocalTime.MIDNIGHT));
        cases.forEach((phrase, expected) -> {
            ParseResult r = parse("Reserve a van on 2026-10-15 " + phrase + " for 2 days");
            assertEquals(List.of(), r.issues(), phrase);
            assertEquals(expected, r.startTime(), phrase);
        });
    }

    @Test
    void dates() {
        Map<String, LocalDate> cases = Map.of(
                "on 5 Nov 2026", LocalDate.of(2026, 11, 5),
                "on 1st of December 2026", LocalDate.of(2026, 12, 1),
                "on Sept 3 2026", LocalDate.of(2026, 9, 3),
                "on 31/12/2026", LocalDate.of(2026, 12, 31),
                "on 12/31/2026", LocalDate.of(2026, 12, 31), // second field > 12 => month-first
                "on 05/05/2026", LocalDate.of(2026, 5, 5), // both readings agree
                "on 15.10.2026", LocalDate.of(2026, 10, 15),
                "on Thursday 15 October 2026", LocalDate.of(2026, 10, 15)); // weekday is consistent
        cases.forEach((phrase, expected) -> {
            ParseResult r = parse("Reserve a van " + phrase + " at 10:00 for 2 days");
            assertEquals(List.of(), r.issues(), phrase);
            assertEquals(expected, r.startDate(), phrase);
        });
    }

    @Test
    void relativeDatesUseTheInjectedClock() {
        Map<String, LocalDate> cases = Map.of(
                "today", LocalDate.of(2026, 10, 8),
                "tomorrow", LocalDate.of(2026, 10, 9),
                "the day after tomorrow", LocalDate.of(2026, 10, 10),
                "in 3 days", LocalDate.of(2026, 10, 11),
                "in two weeks", LocalDate.of(2026, 10, 22));
        cases.forEach((phrase, expected) -> {
            ParseResult r = parse("Reserve a sedan " + phrase + " at 18:00 for 2 days");
            assertEquals(List.of(), r.issues(), phrase);
            assertEquals(expected, r.startDate(), phrase);
            assertEquals(2, r.durationDays(), phrase); // "in 3 days" is not the duration
        });
    }

    // -------------------------------------------------------------------- missing

    @Test
    void everythingMissing() {
        ParseResult r = parse("Reserve a car");
        assertFalse(r.isComplete());
        assertEquals(Set.of(
                new Problem(CAR_TYPE, MISSING),
                new Problem(START_DATE, MISSING),
                new Problem(START_TIME, MISSING),
                new Problem(DURATION, MISSING)), problems(r));
    }

    @Test
    void eachFieldIsReportedIndividually() {
        Map<String, Problem> cases = Map.of(
                "Reserve from 15 October 2026 at 10:00 for 3 days", new Problem(CAR_TYPE, MISSING),
                "Reserve an SUV at 10:00 for 3 days", new Problem(START_DATE, MISSING),
                "Reserve an SUV on 15 October 2026 for 3 days", new Problem(START_TIME, MISSING),
                "Reserve an SUV from 15 October 2026 at 10:00", new Problem(DURATION, MISSING));
        cases.forEach((text, expected) -> {
            ParseResult r = parse(text);
            assertEquals(Set.of(expected), problems(r), text);
            assertFalse(r.isComplete(), text);
        });
    }

    @Test
    void partialResultsArePreserved() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 for 3 days");
        assertEquals(CarType.SUV, r.carType());
        assertEquals(LocalDate.of(2026, 10, 15), r.startDate());
        assertEquals(3, r.durationDays());
        assertTrue(r.start().isEmpty()); // date known, time missing
    }

    @Test
    void emptyInput() {
        for (String text : new String[] {"", "   ", null}) {
            assertEquals(only(REQUEST, MISSING), problems(parse(text)), String.valueOf(text));
        }
    }

    // ------------------------------------------------------------------ ambiguous

    @Test
    void twoCarTypes() {
        ParseResult r = parse("Reserve an SUV or a van from 15 October 2026 at 10:00 for 3 days");
        assertEquals(only(CAR_TYPE, AMBIGUOUS), problems(r));
        assertNull(r.carType());
    }

    @Test
    void bareHourWithoutAmPm() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10 for 3 days");
        assertEquals(only(START_TIME, AMBIGUOUS), problems(r));
        assertTrue(r.issues().get(0).message().contains("22:00"));
    }

    @Test
    void vagueTimeOfDay() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 in the morning for 3 days");
        assertEquals(only(START_TIME, AMBIGUOUS), problems(r));
    }

    @Test
    void conflictingTimes() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10:00 or 14:00 for 3 days");
        assertEquals(only(START_TIME, AMBIGUOUS), problems(r));
    }

    @Test
    void dayMonthVersusMonthDay() {
        ParseResult r = parse("Reserve an SUV on 03/04/2026 at 10:00 for 3 days");
        assertEquals(only(START_DATE, AMBIGUOUS), problems(r));
        String message = r.issues().get(0).message();
        assertTrue(message.contains("3 April 2026"), message);
        assertTrue(message.contains("4 March 2026"), message);
    }

    @Test
    void missingYearIsQuestionedWithASuggestion() {
        ParseResult r = parse("Reserve an SUV on 15 October at 10:00 for 3 days");
        assertEquals(only(START_DATE, AMBIGUOUS), problems(r));
        assertTrue(r.issues().get(0).message().contains("15 October 2026"));
    }

    @Test
    void missingYearRollsToNextYearWhenDateHasPassed() {
        ParseResult r = parse("Reserve an SUV on 1 March at 10:00 for 3 days");
        assertTrue(r.issues().get(0).message().contains("1 March 2027"));
    }

    @Test
    void weekdayWithoutDate() {
        ParseResult r = parse("Reserve an SUV on Friday at 10:00 for 3 days");
        assertEquals(only(START_DATE, AMBIGUOUS), problems(r));
    }

    @Test
    void vagueDatePhrase() {
        ParseResult r = parse("Reserve an SUV next week at 10:00 for 3 days");
        assertEquals(only(START_DATE, AMBIGUOUS), problems(r));
    }

    @Test
    void weekdayContradictingDate() {
        ParseResult r = parse("Reserve an SUV on Friday 15 October 2026 at 10:00 for 3 days");
        assertEquals(only(START_DATE, AMBIGUOUS), problems(r));
        assertTrue(r.issues().get(0).message().contains("Thursday"));
    }

    @Test
    void twoDifferentDates() {
        ParseResult r = parse("Reserve an SUV from 15 October 2026 to 18 October 2026 at 10:00");
        assertTrue(problems(r).contains(new Problem(START_DATE, AMBIGUOUS)));
        assertNull(r.startDate());
    }

    @Test
    void conflictingDurations() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10:00 for 3 days or 5 days");
        assertEquals(only(DURATION, AMBIGUOUS), problems(r));
        assertNull(r.durationDays());
    }

    @Test
    void partialDurationsAreNotGuessed() {
        Map<String, Problem> cases = Map.of(
                "Reserve an SUV on 15 October 2026 at 10:00 for 2 or 3 days", new Problem(DURATION, AMBIGUOUS),
                "Reserve an SUV on 15 October 2026 at 10:00 for 3 days or 5", new Problem(DURATION, AMBIGUOUS),
                "Reserve an SUV on 15 October 2026 at 10:00 for twenty one days", new Problem(DURATION, UNSUPPORTED),
                "Reserve an SUV on 15 October 2026 at 10:00 for twenty-one days", new Problem(DURATION, UNSUPPORTED),
                "Reserve an SUV on 15 October 2026 at 10:00 for half a day", new Problem(DURATION, UNSUPPORTED));
        cases.forEach((text, expected) -> {
            ParseResult r = parse(text);
            assertEquals(Set.of(expected), problems(r), text + " -> " + r.issues());
            assertNull(r.durationDays(), text);
            assertFalse(r.isComplete(), text);
        });
    }

    @Test
    void partialDatesAreNotGuessed() {
        for (String text : List.of(
                "Reserve an SUV from 15 or 16 October 2026 at 10:00 for 3 days",
                "Reserve an SUV from 15 to 18 October 2026 at 10:00 for 3 days",
                "Reserve an SUV on October 15 or 16, 2026 at 10:00 for 3 days",
                "Reserve an SUV on October 15 to 18, 2026 at 10:00 for 3 days")) {
            ParseResult r = parse(text);
            assertEquals(only(START_DATE, AMBIGUOUS), problems(r), text + " -> " + r.issues());
            assertNull(r.startDate(), text);
            assertFalse(r.isComplete(), text);
        }
    }

    @Test
    void durationWithoutUnit() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10:00 for 3");
        assertEquals(only(DURATION, AMBIGUOUS), problems(r));
    }

    // ---------------------------------------------------------------- unsupported

    @Test
    void unknownCarType() {
        ParseResult r = parse("Reserve a truck on 15 October 2026 at 10:00 for 3 days");
        assertEquals(only(CAR_TYPE, UNSUPPORTED), problems(r));
        assertTrue(r.issues().get(0).message().contains("sedan, SUV, van"));
        assertNull(r.carType());
    }

    @Test
    void unsupportedTypeWinsOverSupportedOne() {
        ParseResult r = parse("Reserve an SUV or a convertible on 15 October 2026 at 10:00 for 3 days");
        assertEquals(only(CAR_TYPE, UNSUPPORTED), problems(r));
    }

    @Test
    void multipleCars() {
        for (String quantity : List.of("2 SUVs", "two vans", "a couple of SUVs", "zero SUVs")) {
            ParseResult r = parse("Reserve " + quantity + " on 15 October 2026 at 10:00 for 3 days");
            assertEquals(only(CAR_TYPE, UNSUPPORTED), problems(r), quantity + " -> " + r.issues());
            assertNull(r.carType(), quantity);
            assertFalse(r.isComplete(), quantity);
        }
    }

    @Test
    void digitsFromDatesAreNotMistakenForQuantities() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10:00 for 3 days");
        assertEquals(List.of(), r.issues());
    }

    @Test
    void hours() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10:00 for 5 hours");
        assertEquals(only(DURATION, UNSUPPORTED), problems(r));
    }

    @Test
    void fractionalDays() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10:00 for 1.5 days");
        assertEquals(only(DURATION, UNSUPPORTED), problems(r));
    }

    @Test
    void otherIntents() {
        for (String text : List.of(
                "Cancel my SUV reservation",
                "Please extend my rental by 2 days",
                "Please change my reservation",
                "Do not reserve an SUV from 15 October 2026 at 10:00 for 3 days",
                "I do not want to reserve an SUV from 15 October 2026 at 10:00 for 3 days",
                "I cannot reserve an SUV from 15 October 2026 at 10:00 for 3 days",
                "I can't reserve an SUV from 15 October 2026 at 10:00 for 3 days")) {
            ParseResult r = parse(text);
            assertEquals(only(REQUEST, UNSUPPORTED), problems(r), text + " -> " + r.issues());
            assertFalse(r.isComplete(), text);
        }
    }

    @Test
    void incidentalChangeDoesNotBlockAReservation() {
        ParseResult r = parse("Reserve an SUV from 15 October 2026 at 10:00 for 3 days, no change to the time");
        assertTrue(r.isComplete(), r.issues().toString());
        assertEquals(CarType.SUV, r.carType());
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 0), r.start().orElseThrow());
        assertEquals(3, r.durationDays());
    }

    // -------------------------------------------------------------------- invalid

    @Test
    void impossibleDates() {
        for (String date : List.of("31 February 2026", "2026-02-30", "29 February 2027", "31/04/2026", "00/05/2026")) {
            ParseResult r = parse("Reserve an SUV on " + date + " at 10:00 for 3 days");
            assertEquals(only(START_DATE, INVALID), problems(r), date);
        }
    }

    @Test
    void dateFarInTheFutureDoesNotOverflow() {
        ParseResult r = parse("Reserve an SUV in 99999999999 days at 10:00 for 3 days");
        assertEquals(only(START_DATE, INVALID), problems(r));
    }

    @Test
    void impossibleTimes() {
        for (String time : List.of("at 25:00", "at 10:75", "at 13pm", "at 0am", "at 99", "at 10:00:30")) {
            ParseResult r = parse("Reserve an SUV on 15 October 2026 " + time + " for 3 days");
            assertEquals(only(START_TIME, INVALID), problems(r), time);
        }
    }

    @Test
    void zeroDayRental() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10:00 for 0 days");
        assertEquals(only(DURATION, INVALID), problems(r));
    }

    @Test
    void absurdlyLongDurationDoesNotOverflow() {
        ParseResult r = parse("Reserve an SUV on 15 October 2026 at 10:00 for 99999999999 weeks");
        assertTrue(r.isComplete()); // parsed fine; the service rejects it as out of range
        assertEquals(Integer.MAX_VALUE, r.durationDays());
    }
}
