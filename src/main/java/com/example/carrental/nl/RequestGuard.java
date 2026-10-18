package com.example.carrental.nl;

import static com.example.carrental.nl.IssueKind.UNSUPPORTED;
import static com.example.carrental.nl.ParserLexicon.CI;
import static com.example.carrental.nl.RequestField.REQUEST;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rejects negation and cancel/modify intents before field extraction. */
final class RequestGuard {

    /** A request to cancel or modify. Bare "change" is not enough ("no change to the time"). */
    private static final Pattern INTENT = Pattern.compile(
            "\\b(cancel|cancell?ing|modify|amend|extend|postpone|reschedule|refund)\\b"
                    + "|\\bchange\\s+(?:my|the|this|a|an)\\b", CI);
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(?:do\\s+not|don['’]?t|dont|never|not|cannot|can['’]?t)\\s+"
                    + "(?:\\w+\\s+){0,3}(?:reserve|book|rent)\\b", CI);

    private RequestGuard() {
    }

    /** Empty if the request may proceed; otherwise an unsupported {@link ParseResult}. */
    static Optional<ParseResult> refuse(String original) {
        Matcher negation = NEGATION.matcher(original);
        if (negation.find()) {
            return Optional.of(unsupportedRequest(original, negation.group()));
        }
        Matcher intent = INTENT.matcher(original);
        if (intent.find()) {
            return Optional.of(unsupportedRequest(original, intent.group()));
        }
        return Optional.empty();
    }

    private static ParseResult unsupportedRequest(String original, String phrase) {
        return new ParseResult(original, null, null, null, null, List.of(new Issue(UNSUPPORTED, REQUEST,
                "'%s' is not supported; I can only create new reservations.".formatted(phrase.strip()))));
    }
}
