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
import java.util.function.Consumer;
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

    private static final List<DatePattern> NAMED_MONTH_PATTERNS = List.of(
            new DatePattern(DAY_MONTH, 1, 2),
            new DatePattern(MONTH_DAY, 2, 1));

    private static final List<RelativeWord> RELATIVE_WORDS = List.of(
            new RelativeWord(DAY_AFTER_TOMORROW, 2),
            new RelativeWord(TOMORROW, 1),
            new RelativeWord(TODAY, 0));

    private final Clock clock;

    DateExtractor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Found<LocalDate> extract(String input) {
        Extraction ctx = new Extraction(input, LocalDate.now(clock));

        collectIso(ctx);
        collectNumeric(ctx);
        rejectAlternatives(ctx);
        collectNamedMonthDates(ctx);
        collectInNDays(ctx);
        collectRelativeWords(ctx);
        VagueHints vague = collectVagueHints(ctx);

        return resolve(ctx, vague);
    }

    private static void collectIso(Extraction ctx) {
        ctx.forEach(ISO, m -> ctx.addValid(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                m.group()));
    }

    private static void collectNumeric(Extraction ctx) {
        ctx.forEach(NUMERIC, m -> {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int year = Integer.parseInt(m.group(3));
            if (a > 12 || a == b || a < 1 || b < 1) {
                // Only day-first is possible, or both readings agree.
                ctx.addValid(year, b, a, m.group());
            } else if (b > 12) {
                // Only month-first is possible.
                ctx.addValid(year, a, b, m.group());
            } else {
                LocalDate dayFirst = LocalDate.of(year, b, a);
                LocalDate monthFirst = LocalDate.of(year, a, b);
                ctx.issues.add(new Issue(AMBIGUOUS, START_DATE,
                        "'%s' could mean %s or %s; write the month as a word or use YYYY-MM-DD."
                                .formatted(m.group(), HumanDates.date(dayFirst), HumanDates.date(monthFirst))));
            }
        });
    }

    private static void rejectAlternatives(Extraction ctx) {
        Consumer<MatchResult> reject = m -> ctx.issues.add(ambiguousAlternativeDate(m.group()));
        ctx.forEach(DAY_ALTERNATIVES, reject);
        ctx.forEach(MONTH_DAY_ALTERNATIVES, reject);
    }

    private static void collectNamedMonthDates(Extraction ctx) {
        for (DatePattern rule : NAMED_MONTH_PATTERNS) {
            ctx.forEach(rule.pattern(), m -> {
                int day = Integer.parseInt(m.group(rule.dayGroup()));
                int month = MONTHS.get(m.group(rule.monthGroup()).toLowerCase(Locale.ROOT));
                String shown = m.group().strip();
                if (m.group(3) != null) {
                    ctx.addValid(Integer.parseInt(m.group(3)), month, day, shown);
                } else {
                    resolveMissingYear(day, month, shown, ctx.today, ctx.issues);
                }
            });
        }
    }

    private static void collectInNDays(Extraction ctx) {
        ctx.forEach(IN_N, m -> {
            long n = (long) toNumber(m.group(1)).orElse(0);
            long daysAhead = n * (m.group(2).toLowerCase(Locale.ROOT).startsWith("week") ? 7 : 1);
            if (daysAhead > 36_500) {
                ctx.issues.add(new Issue(INVALID, START_DATE, "'%s' is too far in the future.".formatted(m.group())));
            } else {
                ctx.found.add(ctx.today.plusDays(daysAhead));
            }
        });
    }

    private static void collectRelativeWords(Extraction ctx) {
        for (RelativeWord word : RELATIVE_WORDS) {
            Taken taken = take(word.pattern(), ctx.text);
            ctx.text = taken.text();
            if (!taken.matches().isEmpty()) {
                ctx.found.add(ctx.today.plusDays(word.offsetDays()));
            }
        }
    }

    private static VagueHints collectVagueHints(Extraction ctx) {
        Taken weekdays = take(WEEKDAY, ctx.text);
        ctx.text = weekdays.text();
        Taken vague = take(VAGUE_DATE, ctx.text);
        ctx.text = vague.text();
        return new VagueHints(weekdays.matches(), vague.matches());
    }

    private static Found<LocalDate> resolve(Extraction ctx, VagueHints vague) {
        TreeSet<LocalDate> distinct = new TreeSet<>(ctx.found);
        if (distinct.size() > 1) {
            ctx.issues.add(new Issue(AMBIGUOUS, START_DATE,
                    "Several dates found (%s); I need one start date and a number of days."
                            .formatted(distinct.stream().map(HumanDates::date).collect(Collectors.joining(", ")))));
            return ctx.incomplete();
        }

        if (distinct.size() == 1) {
            LocalDate result = distinct.first();
            List<DayOfWeek> mentioned = vague.weekdays().stream()
                    .map(m -> WEEKDAYS.get(m.group(1).toLowerCase(Locale.ROOT)))
                    .toList();
            List<DayOfWeek> wrong = mentioned.stream().filter(day -> day != result.getDayOfWeek()).toList();
            if (!wrong.isEmpty()) {
                ctx.issues.add(new Issue(AMBIGUOUS, START_DATE, "%s is a %s, not a %s. Which did you mean?".formatted(
                        HumanDates.date(result), dayName(result.getDayOfWeek()), dayName(wrong.getFirst()))));
                return ctx.incomplete();
            }
            return ctx.completeIfClean(result);
        }

        if (ctx.issues.isEmpty()) {
            if (vague.hasHints()) {
                String phrase = Stream.concat(vague.weekdays().stream(), vague.vague().stream())
                        .findFirst()
                        .orElseThrow()
                        .group()
                        .strip();
                ctx.issues.add(new Issue(AMBIGUOUS, START_DATE, "'%s' is too vague; give an explicit date such as %s."
                        .formatted(phrase, HumanDates.date(ctx.today.plusDays(7)))));
            } else {
                ctx.issues.add(new Issue(MISSING, START_DATE, "No start date found (e.g. '15 October 2026')."));
            }
        }
        return ctx.incomplete();
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

    /** Mutable scratch space for one extraction pass. */
    private static final class Extraction {
        private final LocalDate today;
        private final List<LocalDate> found = new ArrayList<>();
        private final List<Issue> issues = new ArrayList<>();
        private String text;

        private Extraction(String input, LocalDate today) {
            this.text = input;
            this.today = today;
        }

        private void forEach(Pattern pattern, Consumer<MatchResult> action) {
            Taken taken = take(pattern, text);
            text = taken.text();
            taken.matches().forEach(action);
        }

        private void addValid(int year, int month, int day, String shown) {
            DateExtractor.addValid(found, issues, year, month, day, shown);
        }

        private Found<LocalDate> incomplete() {
            return new Found<>(null, text, issues);
        }

        private Found<LocalDate> completeIfClean(LocalDate result) {
            return new Found<>(issues.isEmpty() ? result : null, text, issues);
        }
    }

    private record VagueHints(List<MatchResult> weekdays, List<MatchResult> vague) {
        private boolean hasHints() {
            return !weekdays.isEmpty() || !vague.isEmpty();
        }
    }

    private record DatePattern(Pattern pattern, int dayGroup, int monthGroup) {
    }

    private record RelativeWord(Pattern pattern, int offsetDays) {
    }
}
