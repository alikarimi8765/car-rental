package com.example.carrental.repository;

import com.example.carrental.model.CarType;
import com.example.carrental.model.Reservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Stored reservations. The application uses the JPA/H2 implementation. */
public interface ReservationStore {

    int capacity(CarType type);

    int free(CarType type, LocalDateTime start, LocalDateTime end);

    List<Reservation> all();

    List<Reservation> ofType(CarType type);

    Optional<LocalDateTime> earliestStart(CarType type, LocalDateTime notBefore, int days);

    Reservation add(CarType type, LocalDateTime start, int days, String customer);

    Reservation remove(String reservationId);
}
