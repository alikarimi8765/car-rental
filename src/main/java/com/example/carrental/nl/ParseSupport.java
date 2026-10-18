package com.example.carrental.nl;

import static com.example.carrental.nl.RequestField.DURATION;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/** Shared extraction helpers: mask matches, collect issues, carry residual text. */
final class ParseSupport {

    private ParseSupport() {
    }

    /** Outcome of one extraction pass: the value (or null), the masked text, and any issues. */
    record Found<T>(T value, String text, List<Issue> issues) {
    }

    /** Matches of one pattern plus the text with those spans blanked out. */
    record Taken(List<MatchResult> matches, String text) {
    }

    static Taken take(Pattern pattern, String text) {
        List<MatchResult> matches = pattern.matcher(text).results().toList();
        StringBuilder masked = new StringBuilder(text);
        for (MatchResult m : matches) {
            masked.replace(m.start(), m.end(), " ".repeat(m.end() - m.start()));
        }
        return new Taken(matches, masked.toString());
    }

    /** Mask every match and record one issue per match. The value is never guessed from these spans. */
    static String reject(Pattern pattern, String text, List<Issue> issues, IssueKind kind, String message) {
        Taken taken = take(pattern, text);
        for (MatchResult match : taken.matches()) {
            issues.add(new Issue(kind, DURATION, message.formatted(match.group().strip())));
        }
        return taken.text();
    }
}
