package com.example.carrental.nl;

import static com.example.carrental.nl.IssueKind.AMBIGUOUS;
import static com.example.carrental.nl.IssueKind.INVALID;
import static com.example.carrental.nl.IssueKind.MISSING;
import static com.example.carrental.nl.ParserLexicon.CI;
import static com.example.carrental.nl.ParseSupport.take;
import static com.example.carrental.nl.RequestField.START_TIME;

import com.example.carrental.nl.ParseSupport.Found;
import com.example.carrental.nl.ParseSupport.Taken;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Extracts a single start time from residual NL text. */
final class TimeExtractor {

    // Optional :ss so "10:00:00" is 10:00; a non-zero second is rejected.
    private static final Pattern NOON = Pattern.compile("\\b(noon|midday|midnight)\\b", CI);
    private static final Pattern HOUR_MINUTE = Pattern.compile(
            "(?<![\\d:.])(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?![\\d:])(?:\\s*([ap])\\.?m\\b\\.?)?", CI);
    private static final Pattern HOUR_AM_PM = Pattern.compile("(?<![\\d:.])(\\d{1,2})\\s*([ap])\\.?m\\b\\.?", CI);
    private static final Pattern BARE_AT = Pattern.compile("\\bat\\s+(\\d{1,2})(?![\\d:.])\\b", CI);
    private static final Pattern VAGUE_TIME =
            Pattern.compile("\\b(morning|afternoon|evening|tonight|night|lunchtime)\\b", CI);

    private TimeExtractor() {
    }

    static Found<LocalTime> extract(String input) {
        TreeSet<LocalTime> found = new TreeSet<>();
        List<Issue> issues = new ArrayList<>();
        String text = input;

        Taken taken = take(NOON, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            found.add(m.group(1).equalsIgnoreCase("midnight") ? LocalTime.MIDNIGHT : LocalTime.NOON);
        }

        taken = take(HOUR_MINUTE, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            String seconds = m.group(3);
            String meridiem = m.group(4);
            boolean invalid = minute > 59
                    || (seconds != null && Integer.parseInt(seconds) != 0)
                    || (meridiem == null && hour > 23)
                    || (meridiem != null && (hour < 1 || hour > 12));
            if (invalid) {
                issues.add(invalidTime(m));
            } else if (meridiem != null) {
                found.add(to24Hour(hour, minute, meridiem));
            } else { // HH:MM without am/pm is read as a 24-hour clock
                found.add(LocalTime.of(hour, minute));
            }
        }

        taken = take(HOUR_AM_PM, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            int hour = Integer.parseInt(m.group(1));
            if (hour < 1 || hour > 12) {
                issues.add(invalidTime(m));
            } else {
                found.add(to24Hour(hour, 0, m.group(2)));
            }
        }

        taken = take(BARE_AT, text);
        text = taken.text();
        for (MatchResult m : taken.matches()) {
            int hour = Integer.parseInt(m.group(1));
            if (hour > 23) {
                issues.add(invalidTime(m));
            } else if (hour >= 1 && hour <= 12) {
                issues.add(new Issue(AMBIGUOUS, START_TIME,
                        "'%s' could be %02d:00 or %02d:00; add am/pm or use 24-hour time (e.g. %02d:00)."
                                .formatted(m.group().strip(), hour, (hour + 12) % 24, hour)));
            } else {
                found.add(LocalTime.of(hour, 0)); // 0 or 13..23 can only be a 24-hour clock
            }
        }

        if (!issues.isEmpty()) {
            return new Found<>(null, text, issues);
        }
        if (found.size() > 1) {
            issues.add(new Issue(AMBIGUOUS, START_TIME, "Several times found (%s); which one is the pick-up time?"
                    .formatted(found.stream().map(LocalTime::toString).collect(Collectors.joining(", ")))));
            return new Found<>(null, text, issues);
        }
        if (found.size() == 1) {
            return new Found<>(found.first(), text, issues);
        }

        Matcher vague = VAGUE_TIME.matcher(text);
        if (vague.find()) {
            issues.add(new Issue(AMBIGUOUS, START_TIME,
                    "'%s' is not a specific time; give a time such as 10:00.".formatted(vague.group())));
        } else {
            issues.add(new Issue(MISSING, START_TIME, "No start time found (e.g. 'at 10:00')."));
        }
        return new Found<>(null, text, issues);
    }

    private static Issue invalidTime(MatchResult m) {
        return new Issue(INVALID, START_TIME, "'%s' is not a valid time of day.".formatted(m.group().strip()));
    }

    private static LocalTime to24Hour(int hour, int minute, String meridiem) {
        return LocalTime.of(hour % 12 + (meridiem.equalsIgnoreCase("p") ? 12 : 0), minute);
    }
}
