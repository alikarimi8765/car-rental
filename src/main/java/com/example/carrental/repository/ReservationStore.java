package com.example.carrental.repository;

import com.example.carrental.model.CarType;
import com.example.carrental.model.Reservation;

import java.util.List;
import java.util.Optional;

/**
 * Persistence of reservations and nothing else. Availability and capacity decisions
 * belong to the domain ({@code Availability}, {@code CarRentalService}); this port only
 * stores and loads.
 */
public interface ReservationStore {

    /** All reservations ordered by start time, then id. */
    List<Reservation> all();

    /** Reservations of one car type ordered by start time, then id. */
    List<Reservation> ofType(CarType type);

    Optional<Reservation> find(String reservationId);

    Reservation save(Reservation reservation);

    /** Deletes and returns a reservation; throws {@code ReservationNotFoundException} if absent. */
    Reservation remove(String reservationId);
}
