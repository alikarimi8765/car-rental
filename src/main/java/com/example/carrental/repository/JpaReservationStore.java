package com.example.carrental.repository;

import com.example.carrental.exception.ReservationNotFoundException;
import com.example.carrental.mapper.ReservationMapper;
import com.example.carrental.model.CarType;
import com.example.carrental.model.Reservation;
import com.example.carrental.repository.entity.ReservationEntity;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Reservations stored in H2 through Spring Data JPA. Contains no booking rules. */
@Repository
public class JpaReservationStore implements ReservationStore {

    private static final Comparator<Reservation> BY_START_THEN_ID =
            Comparator.comparing(Reservation::start).thenComparing(Reservation::id);

    private final ReservationRepository reservations;
    private final ReservationMapper reservationMapper;

    public JpaReservationStore(ReservationRepository reservations, ReservationMapper reservationMapper) {
        this.reservations = reservations;
        this.reservationMapper = reservationMapper;
    }

    @Override
    public List<Reservation> all() {
        return sorted(reservations.findAll().stream().map(reservationMapper::toDomain));
    }

    @Override
    public List<Reservation> ofType(CarType type) {
        return sorted(reservations.findByCarType(type).stream().map(reservationMapper::toDomain));
    }

    @Override
    public Optional<Reservation> find(String reservationId) {
        return reservations.findById(reservationId).map(reservationMapper::toDomain);
    }

    @Override
    public Reservation save(Reservation reservation) {
        reservations.saveAndFlush(reservationMapper.toEntity(reservation));
        return reservation;
    }

    @Override
    public Reservation remove(String reservationId) {
        ReservationEntity entity = reservations.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        Reservation reservation = reservationMapper.toDomain(entity);
        reservations.delete(entity);
        reservations.flush();
        return reservation;
    }

    private static List<Reservation> sorted(Stream<Reservation> stream) {
        return stream.sorted(BY_START_THEN_ID).toList();
    }
}
