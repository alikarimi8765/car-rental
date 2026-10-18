package com.example.carrental.service;

import com.example.carrental.model.Reservation;
import com.example.carrental.nl.ParseResult;

/**
 * What happened to a free-text reservation request. Sealed, so a
 * {@code switch} over it is checked for exhaustiveness by the compiler:
 * adding a new outcome forces every caller to decide what to do with it.
 */
public sealed interface AssistantResponse {

    String message();

    ParseResult parse();

    /** Why a well-understood request was refused. */
    enum Reason {
        /** The fleet has no free car of that type for the whole period. */
        UNAVAILABLE,
        /** Business-rule violation: start in the past, duration out of range, ... */
        INVALID
    }

    /** The request was understood and a car was booked. */
    record Reserved(Reservation reservation, String message, ParseResult parse) implements AssistantResponse {
    }

    /** Information was missing, ambiguous, unsupported or invalid; nothing was booked. */
    record NeedsClarification(String message, ParseResult parse) implements AssistantResponse {
    }

    /** The request was understood, but the business rules said no. */
    record Rejected(Reason reason, String message, ParseResult parse) implements AssistantResponse {
    }
}
