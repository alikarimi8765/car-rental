package com.example.carrental.nl;

import static com.example.carrental.nl.IssueKind.AMBIGUOUS;
import static com.example.carrental.nl.IssueKind.INVALID;
import static com.example.carrental.nl.IssueKind.MISSING;
import static com.example.carrental.nl.ParserLexicon.CI;
import static com.example.carrental.nl.ParserLexicon.MONTHS;
import static com.example.carrental.nl.ParserLexicon.MONTH_RE;
import static com.example.carrental.nl.ParserLexicon.NUM_WORD_RE;
import static com.example.carrental.nl.ParserLexicon.ORDINAL;
import static com.example.carrental.nl.ParserLexicon.WEEKDAYS;
import static com.example.carrental.nl.ParserLexicon.WEEKDAY_RE;
import static com.example.carrental.nl.ParserLexicon.toNumber;
import static com.example.carrental.nl.ParseSupport.take;
import static com.example.carrental.nl.RequestField.START_DATE;

import com.example.carrental.nl.ParseSupport.Found;
import com.example.carrental.nl.ParseSupport.Taken;
import com.example.carrental.util.HumanDates;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Extracts a single start date from residual NL text. */
final class DateExtractor {

    private static final Pattern ISO = Pattern.compile("(?<![\\d/.:-])(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
    private static final Pattern NUMERIC =
            Pattern.compile("(?<![\\d/.:-])(\\d{1,2})[/.](\\d{1,2})[/.](\\d{4})(?!\\d)");
    private static final Pattern DAY_MONTH = Pattern.compile(
            "(?<![\\d:.])(\\d{1,2})" + ORDINAL + "\\s+(?:of\\s+)?(" + MONTH_RE + ")\\b\\.?(?:,?\\s*(\\d{4})\\b)?", CI);
    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b(" + MONTH_RE + ")\\b\\.?\\s+(\\d{1,2})" + ORDINAL + "(?![\\d:])(?:,?\\s*(\\d{4})\\b)?", CI);
    /** "15 or 16 October" and "15 to 18 October" are not a single start date. */
    private static final Pattern DAY_ALTERNATIVES = Pattern.compile(
            "(?<![\\d:.])(\\d{1,2})" + ORDINAL + "\\s+(?:or|to)\\s+(\\d{1,2})" + ORDINAL
                    + "\\s+(?:of\\s+)?(" + MONTH_RE + ")\\b\\.?(?:,?\\s*(\\d{4})\\b)?", CI);
    /** "October 15 or 16" and "October 15 to 18" are not a single start date. */
    private static final Pattern MONTH_DAY_ALTERNATIVES = Pattern.compile(
            "\\b(" + MONTH_RE + ")\\b\\.?\\s+(\\d{1,2})" + ORDINAL
                    + "\\s+(?:or|to)\\s+(\\d{1,2})" + ORDINAL
                    + "(?![\\d:])(?:,?\\s*(\\d{4})\\b)?", CI);
    private static final Pattern IN_N =
            Pattern.compile("\\bin\\s+(\\d+|" + NUM_WORD_RE + ")\\s+(days?|weeks?)\\b", CI);
    private static final Pattern DAY_AFTER_TOMORROW = Pattern.compile("\\bday\\s+after\\s+tomorrow\\b", CI);
    private static final Pattern TOMORROW = Pattern.compile("\\btomorrow\\b", CI);
    private static final Pattern TODAY = Pattern.compile("\\btoday\\b", CI);
    private static final Pattern WEEKDAY =
            Pattern.compile("\\b(?:(?:next|this|coming|on)\\s+)?(" + WEEKDAY_RE + ")\\b", CI);
    private static final Pattern VAGUE_DATE =
            Pattern.compile("\\b(?:(?:next|this|coming)\\s+(?:week|month|weekend)|weekend)\\b", CI);

    private final Clock clock;

    DateExtractor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Found<LocalDate> extract(String input) {
        LocalDate today = LocalDate.now(clock);
        List<LocalDate> found = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        String text = input;

        Taken taken = take(ISO, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            addValid(found, issues, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), m.group());
        }

        taken = take(NUMERIC, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int year = Integer.parseInt(m.group(3));
            if (a > 12 || a == b || a < 1 || b < 1) { // only day-first is possible, or both readings agree
                addValid(found, issues, year, b, a, m.group());
            } else if (b > 12) { // only month-first is possible
                addValid(found, issues, year, a, b, m.group());
            } else {
                LocalDate dayFirst = LocalDate.of(year, b, a);
                LocalDate monthFirst = LocalDate.of(year, a, b);
                issues.add(new Issue(AMBIGUOUS, START_DATE,
                        "'%s' could mean %s or %s; write the month as a word or use YYYY-MM-DD."
                                .formatted(m.group(), HumanDates.date(dayFirst), HumanDates.date(monthFirst))));
            }
        }

        taken = take(DAY_ALTERNATIVES, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            issues.add(ambiguousAlternativeDate(m.group()));
        }

        taken = take(MONTH_DAY_ALTERNATIVES, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            issues.add(ambiguousAlternativeDate(m.group()));
        }

        for (DatePattern rule : List.of(new DatePattern(DAY_MONTH, 1, 2), new DatePattern(MONTH_DAY, 2, 1))) {
            taken = take(rule.pattern(), text);
            text = taken.text();
            for (MatchResult m : taken.matches()) {
                int day = Integer.parseInt(m.group(rule.dayGroup()));
                int month = MONTHS.get(m.group(rule.monthGroup()).toLowerCase(Locale.ROOT));
                String shown = m.group().strip();
                if (m.group(3) != null) {
                    addValid(found, issues, Integer.parseInt(m.group(3)), month, day, shown);
                } else {
                    resolveMissingYear(day, month, shown, today, issues);
                }
            }
        }

        taken = take(IN_N, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            long n = (long) toNumber(m.group(1)).orElse(0);
            long daysAhead = n * (m.group(2).toLowerCase(Locale.ROOT).startsWith("week") ? 7 : 1);
            if (daysAhead > 36_500) {
                issues.add(new Issue(INVALID, START_DATE, "'%s' is too far in the future.".formatted(m.group())));
            } else {
                found.add(today.plusDays(daysAhead));
            }
        }

        for (RelativeWord word : List.of(new RelativeWord(DAY_AFTER_TOMORROW, 2),
                new RelativeWord(TOMORROW, 1), new RelativeWord(TODAY, 0))) {
            taken = take(word.pattern(), text);
            text = taken.text();
            if (!taken.matches().isEmpty()) {
                found.add(today.plusDays(word.offsetDays()));
            }
        }

        taken = take(WEEKDAY, text);
        text = taken.text();
        List<MatchResult> weekdayMatches = taken.matches();
        List<DayOfWeek> weekdays = weekdayMatches.stream()
                .map(m -> WEEKDAYS.get(m.group(1).toLowerCase(Locale.ROOT)))
                .toList();
        taken = take(VAGUE_DATE, text);
        text = taken.text();
        List<MatchResult> vagueMatches = taken.matches();

        TreeSet<LocalDate> distinct = new TreeSet<>(found);
        if (distinct.size() > 1) {
            issues.add(new Issue(AMBIGUOUS, START_DATE, "Several dates found (%s); I need one start date and a number of days."
                    .formatted(distinct.stream().map(HumanDates::date).collect(Collectors.joining(", ")))));
            return new Found<>(null, text, issues);
        }

        if (distinct.size() == 1) {
            LocalDate result = distinct.first();
            List<DayOfWeek> wrong = weekdays.stream().filter(day -> day != result.getDayOfWeek()).toList();
            if (!wrong.isEmpty()) {
                issues.add(new Issue(AMBIGUOUS, START_DATE, "%s is a %s, not a %s. Which did you mean?".formatted(
                        HumanDates.date(result), dayName(result.getDayOfWeek()), dayName(wrong.get(0)))));
                return new Found<>(null, text, issues);
            }
            return new Found<>(issues.isEmpty() ? result : null, text, issues);
        }

        if (issues.isEmpty()) { // nothing found and nothing already complained about
            if (!weekdayMatches.isEmpty() || !vagueMatches.isEmpty()) {
                String vague = Stream.concat(weekdayMatches.stream(), vagueMatches.stream())
                        .findFirst().orElseThrow().group().strip();
                issues.add(new Issue(AMBIGUOUS, START_DATE, "'%s' is too vague; give an explicit date such as %s."
                        .formatted(vague, HumanDates.date(today.plusDays(7)))));
            } else {
                issues.add(new Issue(MISSING, START_DATE, "No start date found (e.g. '15 October 2026')."));
            }
        }
        return new Found<>(null, text, issues);
    }

    private static void addValid(List<LocalDate> found, List<Issue> issues, int year, int month, int day, String shown) {
        try {
            found.add(LocalDate.of(year, month, day));
        } catch (DateTimeException e) {
            issues.add(new Issue(INVALID, START_DATE, "'%s' is not a real calendar date.".formatted(shown)));
        }
    }

    private static Issue ambiguousAlternativeDate(String shown) {
        return new Issue(AMBIGUOUS, START_DATE,
                "'%s' could be more than one start date; give a single date such as 15 October 2026."
                        .formatted(shown.strip()));
    }

    /** No year given: propose the next occurrence, but still ask. */
    private static void resolveMissingYear(int day, int month, String shown, LocalDate today, List<Issue> issues) {
        for (int year = today.getYear(); year < today.getYear() + 5; year++) { // 5 years covers leap days
            try {
                LocalDate candidate = LocalDate.of(year, month, day);
                if (!candidate.isBefore(today)) {
                    issues.add(new Issue(AMBIGUOUS, START_DATE,
                            "No year given in '%s'. Did you mean %s? Please include the year."
                                    .formatted(shown, HumanDates.date(candidate))));
                    return;
                }
            } catch (DateTimeException e) {
                // e.g. 29 February in a non-leap year: try the next year
            }
        }
        issues.add(new Issue(INVALID, START_DATE, "'%s' is not a real calendar date.".formatted(shown)));
    }

    private static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private record DatePattern(Pattern pattern, int dayGroup, int monthGroup) {
    }

    private record RelativeWord(Pattern pattern, int offsetDays) {
    }
}
