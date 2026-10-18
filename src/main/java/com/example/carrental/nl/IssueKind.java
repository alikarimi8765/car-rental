package com.example.carrental.nl;

/** Why a piece of a reservation request could not be turned into structured data. */
public enum IssueKind {
    /** Nothing usable was found ("no start time"). */
    MISSING,
    /** Several readings are plausible ("at 10" -> 10:00 or 22:00). */
    AMBIGUOUS,
    /** Understood, but the system can't do it ("truck", "5 hours", "2 SUVs"). */
    UNSUPPORTED,
    /** Understood, but impossible ("31 February", "25:00"). */
    INVALID
}
