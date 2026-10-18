package com.example.carrental.repository;

import com.example.carrental.model.Availability;
import com.example.carrental.model.CarType;
import com.example.carrental.model.Fleet;
import com.example.carrental.model.Reservation;
import com.example.carrental.model.entity.ReservationEntity;
import com.example.carrental.exception.CarUnavailableException;
import com.example.carrental.exception.ReservationNotFoundException;
import com.example.carrental.mapper.ReservationMapper;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Reservations stored in H2 through Spring Data JPA.
 *
 * <p>The lock is held until the transaction commits. Releasing it earlier would
 * let a second request read the table before the first insert is visible.
 */
@Repository
public class JpaReservationStore implements ReservationStore {

    private final Fleet fleet;
    private final ReservationRepository reservations;
    private final ReservationMapper reservationMapper;
    private final TransactionTemplate transactions;
    private final ReentrantLock lock = new ReentrantLock();

    public JpaReservationStore(
            Fleet fleet,
            ReservationRepository reservations,
            ReservationMapper reservationMapper,
            TransactionTemplate transactions) {
        this.fleet = fleet;
        this.reservations = reservations;
        this.reservationMapper = reservationMapper;
        this.transactions = transactions;
    }

    @Override
    public int capacity(CarType type) {
        return fleet.size(type);
    }

    @Override
    public int free(CarType type, LocalDateTime start, LocalDateTime end) {
        return locked(() -> Availability.free(fleet.size(type), load(type), start, end));
    }

    @Override
    public List<Reservation> all() {
        return locked(() -> sorted(reservations.findAll().stream().map(reservationMapper::toDomain).toList()));
    }

    @Override
    public List<Reservation> ofType(CarType type) {
        return locked(() -> sorted(load(type)));
    }

    @Override
    public Optional<LocalDateTime> earliestStart(CarType type, LocalDateTime notBefore, int days) {
        return locked(() -> Availability.earliestStart(fleet.size(type), load(type), notBefore, days));
    }

    @Override
    public Reservation add(CarType type, LocalDateTime start, int days, String customer) {
        LocalDateTime end = start.plusDays(days);
        return locked(() -> {
            int capacity = fleet.size(type);
            if (Availability.free(capacity, load(type), start, end) <= 0) {
                throw new CarUnavailableException(type, start, end, capacity);
            }
            Reservation reservation = new Reservation(UUID.randomUUID().toString(), type, start, days, customer);
            reservations.saveAndFlush(reservationMapper.toEntity(reservation));
            return reservation;
        });
    }

    @Override
    public Reservation remove(String reservationId) {
        return locked(() -> {
            ReservationEntity entity = reservations.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));
            Reservation reservation = reservationMapper.toDomain(entity);
            reservations.delete(entity);
            reservations.flush();
            return reservation;
        });
    }

    private List<Reservation> load(CarType type) {
        return reservations.findByCarType(type).stream().map(reservationMapper::toDomain).toList();
    }

    private static List<Reservation> sorted(List<Reservation> reservations) {
        return reservations.stream()
                .sorted(Comparator.comparing(Reservation::start).thenComparing(Reservation::id))
                .toList();
    }

    private <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return inTransaction(action);
        } finally {
            lock.unlock();
        }
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }
}
