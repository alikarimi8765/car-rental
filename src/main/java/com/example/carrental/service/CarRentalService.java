package com.example.carrental.service;

import com.example.carrental.config.RentalProperties;
import com.example.carrental.model.CarType;
import com.example.carrental.model.Reservation;
import com.example.carrental.model.ReservationRules;
import com.example.carrental.repository.ReservationStore;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Books cars by type. Capacity is a count per type: a request is accepted when
 * the overlapping bookings of that type stay below the fleet size for the whole
 * rental. The choice of a physical car is left until pick-up.
 */
@Service
public class CarRentalService {

    private final ReservationRules rules;
    private final ReservationStore store;

    public CarRentalService(Clock clock, RentalProperties properties, ReservationStore store) {
        this.rules = new ReservationRules(clock, properties.maxRentalDays());
        this.store = Objects.requireNonNull(store, "store");
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
        return store.capacity(type);
    }

    /** How many cars of {@code type} are free for the entire period. */
    public int availableCount(CarType type, LocalDateTime start, int days) {
        rules.check(type, start, days);
        return store.free(type, start, start.plusDays(days));
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
        return store.earliestStart(type, notBefore, days);
    }

    public Reservation reserve(CarType type, LocalDateTime start, int days) {
        return reserve(type, start, days, null);
    }

    public Reservation reserve(CarType type, LocalDateTime start, int days, String customer) {
        rules.check(type, start, days);
        return store.add(type, start, days, customer);
    }

    public Reservation cancel(String reservationId) {
        return store.remove(reservationId);
    }
}
