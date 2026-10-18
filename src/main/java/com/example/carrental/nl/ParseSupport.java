package com.example.carrental.nl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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

    /**
     * Mutable scratch for one field extraction: residual text + issues.
     * Each {@link #forEach}/{@link #reject} masks matches so later passes cannot re-read them.
     */
    static final class Scratch {
        private String text;
        private final List<Issue> issues = new ArrayList<>();

        Scratch(String input) {
            this.text = input == null ? "" : input;
        }

        String text() {
            return text;
        }

        List<Issue> issues() {
            return issues;
        }

        boolean hasIssues() {
            return !issues.isEmpty();
        }

        void forEach(Pattern pattern, Consumer<MatchResult> action) {
            Taken taken = take(pattern, text);
            text = taken.text();
            taken.matches().forEach(action);
        }

        /** Mask every match and record one issue per match. Never guesses a value from these spans. */
        void reject(Pattern pattern, IssueKind kind, RequestField field, String message) {
            forEach(pattern, match -> issues.add(
                    new Issue(kind, field, message.formatted(match.group().strip()))));
        }

        <T> Found<T> done(T value) {
            return new Found<>(value, text, issues);
        }

        <T> Found<T> empty() {
            return new Found<>(null, text, issues);
        }
    }
}
