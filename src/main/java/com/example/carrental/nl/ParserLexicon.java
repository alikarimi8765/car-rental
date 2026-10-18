package com.example.carrental.nl;

import static java.util.Map.entry;

import com.example.carrental.model.CarType;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Shared vocabulary and regex fragments for the NL reservation parser. */
final class ParserLexicon {

    static final Map<String, Integer> NUMBER_WORDS = numberWords();
    static final Map<String, Integer> ARTICLES = Map.of("a", 1, "an", 1);

    static final Map<String, Integer> MONTHS = Map.ofEntries(
            entry("january", 1), entry("jan", 1), entry("february", 2), entry("feb", 2),
            entry("march", 3), entry("mar", 3), entry("april", 4), entry("apr", 4),
            entry("may", 5), entry("june", 6), entry("jun", 6), entry("july", 7), entry("jul", 7),
            entry("august", 8), entry("aug", 8), entry("september", 9), entry("sept", 9),
            entry("sep", 9), entry("october", 10), entry("oct", 10), entry("november", 11),
            entry("nov", 11), entry("december", 12), entry("dec", 12));

    static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "monday", DayOfWeek.MONDAY, "tuesday", DayOfWeek.TUESDAY, "wednesday", DayOfWeek.WEDNESDAY,
            "thursday", DayOfWeek.THURSDAY, "friday", DayOfWeek.FRIDAY, "saturday", DayOfWeek.SATURDAY,
            "sunday", DayOfWeek.SUNDAY);

    static final Map<String, CarType> SUPPORTED_ALIASES = Map.of(
            "sedan", CarType.SEDAN, "saloon", CarType.SEDAN,
            "suv", CarType.SUV, "sport utility vehicle", CarType.SUV,
            "van", CarType.VAN, "minivan", CarType.VAN, "mini van", CarType.VAN);

    /** Known vehicle words we deliberately do not offer. */
    static final Set<String> UNSUPPORTED_ALIASES = Set.of(
            "truck", "pickup", "pickup truck", "convertible", "cabriolet", "coupe", "hatchback",
            "wagon", "motorcycle", "motorbike", "scooter", "bus", "minibus", "limousine", "limo",
            "camper", "campervan", "rv", "bike", "bicycle");

    static final String SUPPORTED_LIST = "sedan, SUV, van";

    static final String MONTH_RE = alternation(MONTHS.keySet(), false);
    static final String NUM_WORD_RE = alternation(NUMBER_WORDS.keySet(), false);
    static final String NUM_OR_ARTICLE_RE =
            alternation(Stream.concat(NUMBER_WORDS.keySet().stream(), ARTICLES.keySet().stream()).toList(), false);
    static final String WEEKDAY_RE = alternation(WEEKDAYS.keySet(), false);
    static final String ORDINAL = "(?:st|nd|rd|th)?";
    static final int CI = Pattern.CASE_INSENSITIVE;

    private ParserLexicon() {
    }

    static OptionalDouble toNumber(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        if (t.matches("\\d+(?:\\.\\d+)?")) {
            return OptionalDouble.of(Double.parseDouble(t));
        }
        Integer word = NUMBER_WORDS.containsKey(t) ? NUMBER_WORDS.get(t) : ARTICLES.get(t);
        return word == null ? OptionalDouble.empty() : OptionalDouble.of(word);
    }

    /** Regex alternation, longest words first; spaces inside a word match spaces or hyphens. */
    static String alternation(Collection<String> words, boolean plural) {
        return words.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(word -> Arrays.stream(word.split(" "))
                        .map(Pattern::quote)
                        .collect(Collectors.joining("[\\s-]+")) + (plural ? "s?" : ""))
                .collect(Collectors.joining("|"));
    }

    private static Map<String, Integer> numberWords() {
        String[] words = ("one two three four five six seven eight nine ten eleven twelve thirteen "
                + "fourteen fifteen sixteen seventeen eighteen nineteen twenty").split(" ");
        Map<String, Integer> numbers = new LinkedHashMap<>();
        numbers.put("zero", 0);
        for (int i = 0; i < words.length; i++) {
            numbers.put(words[i], i + 1);
        }
        numbers.put("thirty", 30);
        return Map.copyOf(numbers);
    }
}
