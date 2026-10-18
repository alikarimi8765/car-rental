package com.example.carrental.nl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.carrental.model.CarType;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

class ParserLexiconTest {

    @Test
    void toNumberParsesDigitsAndDecimals() {
        assertEquals(3.0, ParserLexicon.toNumber("3").orElseThrow());
        assertEquals(2.5, ParserLexicon.toNumber("2.5").orElseThrow());
    }

    @Test
    void toNumberParsesNumberWordsAndArticlesCaseInsensitively() {
        assertEquals(0.0, ParserLexicon.toNumber("zero").orElseThrow());
        assertEquals(1.0, ParserLexicon.toNumber("a").orElseThrow());
        assertEquals(1.0, ParserLexicon.toNumber("an").orElseThrow());
        assertEquals(3.0, ParserLexicon.toNumber("three").orElseThrow());
        assertEquals(20.0, ParserLexicon.toNumber("TWENTY").orElseThrow());
        assertEquals(30.0, ParserLexicon.toNumber("thirty").orElseThrow());
    }

    @Test
    void toNumberIsEmptyForUnknownTokens() {
        OptionalDouble missing = ParserLexicon.toNumber("dozen");
        assertTrue(missing.isEmpty());
        assertTrue(ParserLexicon.toNumber("").isEmpty());
        assertTrue(ParserLexicon.toNumber("3rd").isEmpty());
    }

    @Test
    void alternationPrefersLongerPhrasesAndAllowsHyphenOrSpace() {
        String re = ParserLexicon.alternation(List.of("van", "mini van"), false);
        Pattern pattern = Pattern.compile("\\b(" + re + ")\\b", Pattern.CASE_INSENSITIVE);

        assertEquals("mini van", pattern.matcher("a mini van please").results().findFirst().orElseThrow().group(1));
        assertEquals("mini-van", pattern.matcher("a mini-van please").results().findFirst().orElseThrow().group(1));
        assertEquals("van", pattern.matcher("a van please").results().findFirst().orElseThrow().group(1));
    }

    @Test
    void alternationWithPluralAllowsTrailingS() {
        String re = ParserLexicon.alternation(List.of("suv"), true);
        Pattern pattern = Pattern.compile("\\b(" + re + ")\\b", Pattern.CASE_INSENSITIVE);

        assertTrue(pattern.matcher("suv").find());
        assertTrue(pattern.matcher("SUVs").find());
    }

    @Test
    void vocabularyCoversCoreAliasesMonthsAndWeekdays() {
        assertEquals(CarType.SEDAN, ParserLexicon.SUPPORTED_ALIASES.get("saloon"));
        assertEquals(CarType.SUV, ParserLexicon.SUPPORTED_ALIASES.get("sport utility vehicle"));
        assertEquals(CarType.VAN, ParserLexicon.SUPPORTED_ALIASES.get("mini van"));
        assertTrue(ParserLexicon.UNSUPPORTED_ALIASES.contains("truck"));

        assertEquals(10, ParserLexicon.MONTHS.get("october"));
        assertEquals(10, ParserLexicon.MONTHS.get("oct"));
        assertEquals(DayOfWeek.THURSDAY, ParserLexicon.WEEKDAYS.get("thursday"));
        assertFalse(ParserLexicon.SUPPORTED_LIST.isBlank());
    }
}
