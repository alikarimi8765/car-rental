package com.example.carrental.nl;

/** The part of a reservation request an {@link Issue} refers to. */
public enum RequestField {
    CAR_TYPE,
    START_DATE,
    START_TIME,
    DURATION,
    /** The request as a whole (empty input, or an intent we don't support). */
    REQUEST
}
