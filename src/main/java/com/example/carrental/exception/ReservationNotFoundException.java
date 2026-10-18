package com.example.carrental.exception;

/** No reservation exists with the given id. */
public final class ReservationNotFoundException extends RentalException {

    public ReservationNotFoundException(String id) {
        super("No reservation with id '%s'.".formatted(id));
    }
}
