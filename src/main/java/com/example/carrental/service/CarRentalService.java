package com.example.carrental.service;

import com.example.carrental.config.RentalProperties;
import com.example.carrental.exception.CarUnavailableException;
import com.example.carrental.exception.ReservationNotFoundException;
import com.example.carrental.model.Availability;
import com.example.carrental.model.CarType;
import com.example.carrental.model.Fleet;
import com.example.carrental.model.Reservation;
import com.example.carrental.model.ReservationRules;
import com.example.carrental.repository.ReservationStore;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Books cars by type. Capacity is a count per type: a request is accepted when
 * the overlapping bookings of that type stay below the fleet size for the whole
 * rental. The choice of a physical car is left until pick-up.
 */
@Service
public class CarRentalService {

    private final ReservationRules rules;
    private final Fleet fleet;
    private final ReservationStore store;
    private final BookingLock bookingLock;

    public CarRentalService(
            Clock clock,
            RentalProperties properties,
            Fleet fleet,
            ReservationStore store,
            BookingLock bookingLock) {
        this.rules = new ReservationRules(clock, properties.maxRentalDays());
        this.fleet = Objects.requireNonNull(fleet, "fleet");
        this.store = Objects.requireNonNull(store, "store");
        this.bookingLock = Objects.requireNonNull(bookingLock, "bookingLock");
    }

    public int maxRentalDays() {
        return rules.maxRentalDays();
    }

    public Clock clock() {
        return rules.clock();
    }

    public LocalDateTime now() {
        return rules.now();
    }

    public int capacity(CarType type) {
        return fleet.size(type);
    }

    /** How many cars of {@code type} are free for the entire period. */
    public int availableCount(CarType type, LocalDateTime start, int days) {
        rules.check(type, start, days);
        return Availability.free(fleet.size(type), store.ofType(type), start, start.plusDays(days));
    }

    public boolean isAvailable(CarType type, LocalDateTime start, int days) {
        return availableCount(type, start, days) > 0;
    }

    /** All reservations ordered by start time, then id. */
    public List<Reservation> reservations() {
        return store.all();
    }

    public List<Reservation> reservations(CarType type) {
        return store.ofType(type);
    }

    /**
     * Earliest start at or after {@code notBefore} for which a {@code days}-long rental fits.
     * Empty only when the fleet has no cars of this type.
     */
    public Optional<LocalDateTime> earliestAvailableStart(CarType type, LocalDateTime notBefore, int days) {
        rules.check(type, notBefore, days);
        return Availability.earliestStart(fleet.size(type), store.ofType(type), notBefore, days);
    }

    public Reservation reserve(CarType type, LocalDateTime start, int days) {
        return reserve(type, start, days, null);
    }

    public Reservation reserve(CarType type, LocalDateTime start, int days, String customer) {
        rules.check(type, start, days);
        LocalDateTime end = start.plusDays(days);
        return bookingLock.withLock(type, () -> {
            int capacity = fleet.size(type);
            if (Availability.free(capacity, store.ofType(type), start, end) <= 0) {
                throw new CarUnavailableException(type, start, end, capacity);
            }
            return store.save(new Reservation(UUID.randomUUID().toString(), type, start, days, customer));
        });
    }

    public Reservation cancel(String reservationId) {
        Reservation existing = store.find(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return bookingLock.withLock(existing.carType(), () -> store.remove(reservationId));
    }
}
