package com.example.carrental.nl;

import com.example.carrental.nl.ParseSupport.Found;

/**
 * One pass of the parsing pipeline: reads a single field from the residual text and
 * returns it together with the text that is left once the match is blanked out.
 *
 * <p>Passes are chained by {@link ReservationParser}, each one receiving the residual
 * text of the previous pass, so one piece of text is never read in two roles.
 *
 * @param <T> the type of value this pass extracts
 */
@FunctionalInterface
interface FieldExtractor<T> {

    Found<T> extract(String text);
}
