package com.example.carrental.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Capacity of one car type over time.
 *
 * <p>A rental {@code [start, end)} fits when, at every instant in that window,
 * fewer than {@code capacity} existing rentals are active. Load only rises when
 * a rental starts, so the peak is at {@code start} or at the start of an
 * overlapping rental inside the window. A car returned at 10:00 can be picked
 * up again at 10:00.
 */
public final class Availability {

    private Availability() {
    }

    public static int free(int capacity, List<Reservation> existing, LocalDateTime start, LocalDateTime end) {
        return capacity - peakLoad(existing, start, end);
    }

    /**
     * Earliest start at or after {@code notBefore} for which a {@code days}-long rental fits.
     * Empty when {@code capacity} is zero.
     *
     * <p>The answer is either {@code notBefore} or the moment an existing rental ends.
     * If a start is feasible but every slightly earlier start is not, the extra sliver
     * can only be full when a rental ends at that start.
     */
    public static Optional<LocalDateTime> earliestStart(
            int capacity, List<Reservation> existing, LocalDateTime notBefore, int days) {
        return candidateStarts(existing, notBefore)
                .filter(candidate -> free(capacity, existing, candidate, candidate.plusDays(days)) > 0)
                .findFirst();
    }

    private static Stream<LocalDateTime> candidateStarts(List<Reservation> existing, LocalDateTime notBefore) {
        return Stream.concat(
                Stream.of(notBefore),
                existing.stream()
                        .map(Reservation::end)
                        .filter(end -> end.isAfter(notBefore))
                        .distinct()
                        .sorted());
    }

    private static int peakLoad(List<Reservation> existing, LocalDateTime start, LocalDateTime end) {
        List<Reservation> overlapping = existing.stream()
                .filter(reservation -> reservation.overlaps(start, end))
                .toList();
        if (overlapping.isEmpty()) {
            return 0;
        }
        TreeSet<LocalDateTime> checkpoints = new TreeSet<>();
        checkpoints.add(start);
        overlapping.stream()
                .map(Reservation::start)
                .filter(reservationStart -> reservationStart.isAfter(start))
                .forEach(checkpoints::add);
        return checkpoints.stream()
                .mapToInt(point -> (int) overlapping.stream().filter(reservation -> reservation.isActiveAt(point)).count())
                .max()
                .orElse(0);
    }
}
