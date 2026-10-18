package com.example.carrental.nl;

import static com.example.carrental.nl.IssueKind.AMBIGUOUS;
import static com.example.carrental.nl.IssueKind.INVALID;
import static com.example.carrental.nl.IssueKind.MISSING;
import static com.example.carrental.nl.IssueKind.UNSUPPORTED;
import static com.example.carrental.nl.ParserLexicon.CI;
import static com.example.carrental.nl.ParserLexicon.NUM_OR_ARTICLE_RE;
import static com.example.carrental.nl.ParserLexicon.NUM_WORD_RE;
import static com.example.carrental.nl.ParserLexicon.toNumber;
import static com.example.carrental.nl.ParseSupport.reject;
import static com.example.carrental.nl.ParseSupport.take;
import static com.example.carrental.nl.RequestField.DURATION;

import com.example.carrental.nl.ParseSupport.Found;
import com.example.carrental.nl.ParseSupport.Taken;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Extracts rental duration in whole days from residual NL text. */
final class DurationExtractor {

    // Ambiguous and unsupported phrases are masked before the plain pattern,
    // so "2 or 3 days" cannot be read as just "3 days"
    private static final String UNIT_RE = "days?|weeks?|hours?|hrs?";
    private static final String NUM_RE = "(?:\\d+(?:\\.\\d+)?|" + NUM_OR_ARTICLE_RE + ")";
    private static final Pattern DURATION_HALF = Pattern.compile(
            "\\bhalf(?:\\s+an?)?\\s+(?:" + UNIT_RE + ")\\b", CI);
    private static final Pattern DURATION_COMPOUND = Pattern.compile(
            "\\b(?:twenty|thirty)(?:\\s*-\\s*|\\s+)(?:" + NUM_WORD_RE + ")(?:\\s*-\\s*|\\s+)(?:" + UNIT_RE + ")\\b", CI);
    private static final Pattern DURATION_OR = Pattern.compile(
            "\\b" + NUM_RE + "\\s+or\\s+" + NUM_RE + "\\s+(?:" + UNIT_RE + ")\\b", CI);
    private static final Pattern DURATION_OR_TRAILING = Pattern.compile(
            "\\b" + NUM_RE + "\\s+(?:" + UNIT_RE + ")\\s+or\\s+" + NUM_RE + "\\b(?!\\s*(?:" + UNIT_RE + "))", CI);
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "\\b(\\d+(?:\\.\\d+)?|" + NUM_OR_ARTICLE_RE + ")(?:\\s*-\\s*|\\s+)(days?|weeks?|hours?|hrs?)\\b", CI);
    private static final Pattern BARE_FOR =
            Pattern.compile("\\bfor\\s+(?:\\d+|" + NUM_WORD_RE + ")\\b(?!\\s*[:\\d])", CI);

    private DurationExtractor() {
    }

    static Found<Integer> extract(String input) {
        List<Issue> issues = new ArrayList<>();
        String text = reject(DURATION_HALF, input, issues, UNSUPPORTED,
                "'%s': only whole days are supported.");
        text = reject(DURATION_COMPOUND, text, issues, UNSUPPORTED,
                "'%s': write the number in digits (e.g. 21 days).");
        text = reject(DURATION_OR, text, issues, AMBIGUOUS,
                "'%s' could be more than one duration; which one do you want?");
        text = reject(DURATION_OR_TRAILING, text, issues, AMBIGUOUS,
                "'%s' could be more than one duration; which one do you want?");

        Taken taken = take(DURATION_PATTERN, text);
        text = taken.text();
        TreeSet<Integer> values = new TreeSet<>();

        for (MatchResult m : taken.matches()) {
            OptionalDouble number = toNumber(m.group(1));
            String unit = m.group(2).toLowerCase(Locale.ROOT);
            String shown = m.group().strip();
            if (unit.startsWith("hour") || unit.startsWith("hr")) {
                issues.add(new Issue(UNSUPPORTED, DURATION,
                        "'%s': rentals are booked in whole days, not hours.".formatted(shown)));
            } else if (number.isEmpty() || number.getAsDouble() != Math.floor(number.getAsDouble())) {
                issues.add(new Issue(UNSUPPORTED, DURATION, "'%s': only whole days are supported.".formatted(shown)));
            } else if (number.getAsDouble() < 1) {
                issues.add(new Issue(INVALID, DURATION, "'%s': a rental must last at least 1 day.".formatted(shown)));
            } else {
                long days = (long) number.getAsDouble() * (unit.startsWith("week") ? 7 : 1);
                values.add((int) Math.min(days, Integer.MAX_VALUE)); // the service rejects absurd values
            }
        }

        if (!issues.isEmpty()) {
            return new Found<>(null, text, issues);
        }
        if (values.size() > 1) {
            issues.add(new Issue(AMBIGUOUS, DURATION, "Several different durations found (%s days); which one do you want?"
                    .formatted(values.stream().map(String::valueOf).collect(Collectors.joining(", ")))));
            return new Found<>(null, text, issues);
        }
        if (values.size() == 1) {
            return new Found<>(values.first(), text, issues);
        }

        Matcher bare = BARE_FOR.matcher(text);
        if (bare.find()) {
            issues.add(new Issue(AMBIGUOUS, DURATION, "'%s' has no unit; did you mean days?".formatted(bare.group())));
        } else {
            issues.add(new Issue(MISSING, DURATION, "No rental duration found (e.g. 'for 3 days')."));
        }
        return new Found<>(null, text, issues);
    }
}
