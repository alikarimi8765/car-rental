package com.example.carrental.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/** English, unambiguous date formatting for messages ("15 October 2026"). */
public final class HumanDates {

    private HumanDates() {
    }

    public static String date(LocalDate date) {
        return "%d %s %d".formatted(
                date.getDayOfMonth(), date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), date.getYear());
    }

    public static String moment(LocalDateTime moment) {
        return "%s at %02d:%02d".formatted(date(moment.toLocalDate()), moment.getHour(), moment.getMinute());
    }
}
