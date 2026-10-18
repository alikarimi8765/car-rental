package com.example.carrental.nl;

import static com.example.carrental.nl.IssueKind.AMBIGUOUS;
import static com.example.carrental.nl.IssueKind.INVALID;
import static com.example.carrental.nl.IssueKind.MISSING;
import static com.example.carrental.nl.ParserLexicon.CI;
import static com.example.carrental.nl.RequestField.START_TIME;

import com.example.carrental.nl.ParseSupport.Found;
import com.example.carrental.nl.ParseSupport.Scratch;

import java.time.LocalTime;
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
        Scratch scratch = new Scratch(input);
        TreeSet<LocalTime> found = new TreeSet<>();

        collectNamedTimes(scratch, found);
        collectHourMinute(scratch, found);
        collectHourAmPm(scratch, found);
        collectBareAt(scratch, found);
        return resolve(scratch, found);
    }

    private static void collectNamedTimes(Scratch scratch, TreeSet<LocalTime> found) {
        scratch.forEach(NOON, m -> found.add(
                m.group(1).equalsIgnoreCase("midnight") ? LocalTime.MIDNIGHT : LocalTime.NOON));
    }

    private static void collectHourMinute(Scratch scratch, TreeSet<LocalTime> found) {
        scratch.forEach(HOUR_MINUTE, m -> {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            String seconds = m.group(3);
            String meridiem = m.group(4);

            if (isInvalidHourMinute(hour, minute, seconds, meridiem)) {
                scratch.issues().add(invalidTime(m));
            } else if (meridiem != null) {
                found.add(to24Hour(hour, minute, meridiem));
            } else {
                // HH:MM without am/pm is read as a 24-hour clock.
                found.add(LocalTime.of(hour, minute));
            }
        });
    }

    private static void collectHourAmPm(Scratch scratch, TreeSet<LocalTime> found) {
        scratch.forEach(HOUR_AM_PM, m -> {
            int hour = Integer.parseInt(m.group(1));
            if (hour < 1 || hour > 12) {
                scratch.issues().add(invalidTime(m));
            } else {
                found.add(to24Hour(hour, 0, m.group(2)));
            }
        });
    }

    private static void collectBareAt(Scratch scratch, TreeSet<LocalTime> found) {
        scratch.forEach(BARE_AT, m -> {
            int hour = Integer.parseInt(m.group(1));
            if (hour > 23) {
                scratch.issues().add(invalidTime(m));
            } else if (hour >= 1 && hour <= 12) {
                scratch.issues().add(new Issue(AMBIGUOUS, START_TIME,
                        "'%s' could be %02d:00 or %02d:00; add am/pm or use 24-hour time (e.g. %02d:00)."
                                .formatted(m.group().strip(), hour, (hour + 12) % 24, hour)));
            } else {
                found.add(LocalTime.of(hour, 0)); // 0 or 13..23 can only be a 24-hour clock
            }
        });
    }

    private static Found<LocalTime> resolve(Scratch scratch, TreeSet<LocalTime> found) {
        if (scratch.hasIssues()) {
            return scratch.empty();
        }
        if (found.size() > 1) {
            scratch.issues().add(new Issue(AMBIGUOUS, START_TIME,
                    "Several times found (%s); which one is the pick-up time?"
                            .formatted(found.stream().map(LocalTime::toString).collect(Collectors.joining(", ")))));
            return scratch.empty();
        }
        if (found.size() == 1) {
            return scratch.done(found.first());
        }

        Matcher vague = VAGUE_TIME.matcher(scratch.text());
        if (vague.find()) {
            scratch.issues().add(new Issue(AMBIGUOUS, START_TIME,
                    "'%s' is not a specific time; give a time such as 10:00.".formatted(vague.group())));
        } else {
            scratch.issues().add(new Issue(MISSING, START_TIME, "No start time found (e.g. 'at 10:00')."));
        }
        return scratch.empty();
    }

    private static boolean isInvalidHourMinute(int hour, int minute, String seconds, String meridiem) {
        return minute > 59
                || (seconds != null && Integer.parseInt(seconds) != 0)
                || (meridiem == null && hour > 23)
                || (meridiem != null && (hour < 1 || hour > 12));
    }

    private static Issue invalidTime(MatchResult m) {
        return new Issue(INVALID, START_TIME, "'%s' is not a valid time of day.".formatted(m.group().strip()));
    }

    private static LocalTime to24Hour(int hour, int minute, String meridiem) {
        return LocalTime.of(hour % 12 + (meridiem.equalsIgnoreCase("p") ? 12 : 0), minute);
    }
}
