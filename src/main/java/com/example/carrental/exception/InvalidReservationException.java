package com.example.carrental.exception;

/** The request itself is malformed (start in the past, bad duration, missing field, ...). */
public final class InvalidReservationException extends RentalException {

    public InvalidReservationException(String message) {
        super(message);
    }
}
