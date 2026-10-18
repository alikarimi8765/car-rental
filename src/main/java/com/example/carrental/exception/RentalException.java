package com.example.carrental.exception;

/**
 * Root of the rental-domain errors. Sealed so that callers (e.g. the REST
 * exception handler) can reason about the complete set of failure modes.
 */
public abstract sealed class RentalException extends RuntimeException
        permits InvalidReservationException, ReservationNotFoundException, CarUnavailableException {

    protected RentalException(String message) {
        super(message);
    }
}
