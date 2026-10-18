package com.example.carrental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.carrental.model.CarType;
import com.example.carrental.model.Reservation;
import com.example.carrental.exception.CarUnavailableException;
import com.example.carrental.exception.InvalidReservationException;
import com.example.carrental.repository.FleetCapacityRepository;
import com.example.carrental.repository.ReservationRepository;
import com.example.carrental.service.AssistantResponse;
import com.example.carrental.service.CarRentalService;
import com.example.carrental.service.RentalAssistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Boots the real application context: configuration properties, wiring, shared clock. */
@SpringBootTest
class CarRentalApplicationTests {

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return TestClocks.fixed(LocalDateTime.of(2026, 10, 8, 17, 49));
        }
    }

    @Autowired
    CarRentalService service;

    @Autowired
    RentalAssistant assistant;

    @Autowired
    ReservationRepository reservations;

    @Autowired
    FleetCapacityRepository fleetCapacities;

    @BeforeEach
    void emptyDatabase() {
        reservations.deleteAll();
    }

    @Test
    void fleetIsStoredInDatabase() {
        assertEquals(3, fleetCapacities.count());
        assertEquals(5, fleetCapacities.findById(CarType.SEDAN).orElseThrow().getSize());
        assertEquals(3, fleetCapacities.findById(CarType.SUV).orElseThrow().getSize());
        assertEquals(2, fleetCapacities.findById(CarType.VAN).orElseThrow().getSize());
        assertEquals(5, service.capacity(CarType.SEDAN));
        assertEquals(3, service.capacity(CarType.SUV));
        assertEquals(2, service.capacity(CarType.VAN));
        assertEquals(30, service.maxRentalDays());
    }

    @Test
    void specExampleWorksThroughTheRealWiring() {
        AssistantResponse response =
                assistant.handle("Reserve an SUV from 15 October 2026 at 10:00 for 3 days");

        AssistantResponse.Reserved reserved = assertInstanceOf(AssistantResponse.Reserved.class, response);
        assertEquals(CarType.SUV, reserved.reservation().carType());
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 0), reserved.reservation().start());
        assertEquals(1, reservations.count());
        assertEquals(CarType.SUV, reservations.findById(reserved.reservation().id()).orElseThrow().getCarType());
        assertDoesNotThrowUuid(reserved.reservation().id());
    }

    @Test
    void eachBookingGetsAUniqueUuid() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        Reservation first = service.reserve(CarType.SUV, start, 1);
        Reservation second = service.reserve(CarType.SUV, start.plusDays(2), 1);
        assertNotEquals(first.id(), second.id());
        assertDoesNotThrowUuid(first.id());
        assertDoesNotThrowUuid(second.id());

        service.cancel(second.id());
        Reservation third = service.reserve(CarType.SUV, start.plusDays(4), 1);

        assertNotEquals(first.id(), third.id());
        assertNotEquals(second.id(), third.id());
        assertTrue(reservations.findById(second.id()).isEmpty());
        assertEquals(2, reservations.count());
    }

    @Test
    void cancelThenRebookSameSlotSucceedsAndRemovesOldRow() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        Reservation held = service.reserve(CarType.SUV, start, 3);
        service.cancel(held.id());

        Reservation replacement = service.reserve(CarType.SUV, start, 3);

        assertEquals(start, replacement.start());
        assertEquals(3, replacement.days());
        assertTrue(reservations.findById(held.id()).isEmpty());
        assertEquals(1, reservations.count());
        assertEquals(replacement.id(), reservations.findAll().getFirst().getId());
    }

    @Test
    void databaseNeverOversells() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            attempts.add(() -> {
                try {
                    service.reserve(CarType.SUV, start, 3);
                    return true;
                } catch (CarUnavailableException e) {
                    return false;
                }
            });
        }

        long successes = 0;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Boolean> result : pool.invokeAll(attempts)) {
                if (result.get()) {
                    successes++;
                }
            }
        }

        assertEquals(3, successes);
        assertEquals(3, reservations.findByCarType(CarType.SUV).size());
    }

    @Test
    void typesAreIndependent() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        for (int i = 0; i < service.capacity(CarType.SEDAN); i++) {
            service.reserve(CarType.SEDAN, start, 3);
        }
        assertEquals(0, service.availableCount(CarType.SEDAN, start, 3));
        assertTrue(service.isAvailable(CarType.SUV, start, 3));
        assertTrue(service.isAvailable(CarType.VAN, start, 3));
    }

    @Test
    void sedanAndVanCapsAreEnforced() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        for (int i = 0; i < 5; i++) {
            service.reserve(CarType.SEDAN, start, 2);
        }
        for (int i = 0; i < 2; i++) {
            service.reserve(CarType.VAN, start, 2);
        }
        assertThrows(CarUnavailableException.class, () -> service.reserve(CarType.SEDAN, start, 2));
        assertThrows(CarUnavailableException.class, () -> service.reserve(CarType.VAN, start, 2));
    }

    @Test
    void assistantAsksForClarificationWhenParseIsIncomplete() {
        AssistantResponse response = assistant.handle("Reserve a car");
        AssistantResponse.NeedsClarification clarification =
                assertInstanceOf(AssistantResponse.NeedsClarification.class, response);
        assertTrue(clarification.message().contains("couldn't complete"));
        assertEquals(0, reservations.count());
    }

    @Test
    void assistantRejectsWhenFleetIsFull() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 15, 10, 0);
        for (int i = 0; i < service.capacity(CarType.SUV); i++) {
            service.reserve(CarType.SUV, start, 3);
        }

        AssistantResponse response =
                assistant.handle("Reserve an SUV from 15 October 2026 at 10:00 for 3 days");
        AssistantResponse.Rejected rejected = assertInstanceOf(AssistantResponse.Rejected.class, response);
        assertEquals(AssistantResponse.Reason.UNAVAILABLE, rejected.reason());
        assertTrue(rejected.message().contains("earliest start") || rejected.message().contains("No SUV"));
        assertEquals(3, reservations.count());
    }

    @Test
    void assistantRejectsDurationBeyondMaxRentalDays() {
        AssistantResponse response =
                assistant.handle("Reserve an SUV on 15 October 2026 at 10:00 for 31 days");
        AssistantResponse.Rejected rejected = assertInstanceOf(AssistantResponse.Rejected.class, response);
        assertEquals(AssistantResponse.Reason.INVALID, rejected.reason());
        assertTrue(rejected.message().contains("between 1 and 30"));
        assertEquals(0, reservations.count());
    }

    @Test
    void assistantRejectsStartInThePast() {
        AssistantResponse response =
                assistant.handle("Reserve an SUV on 1 October 2026 at 10:00 for 3 days");
        AssistantResponse.Rejected rejected = assertInstanceOf(AssistantResponse.Rejected.class, response);
        assertEquals(AssistantResponse.Reason.INVALID, rejected.reason());
        assertTrue(rejected.message().contains("past"));
        assertEquals(0, reservations.count());
    }

    @Test
    void serviceRejectsAbsurdDurationEvenWhenParserAcceptsIt() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 15, 10, 0);
        assertThrows(
                InvalidReservationException.class,
                () -> service.reserve(CarType.SUV, start, Integer.MAX_VALUE));
        assertEquals(0, reservations.count());
    }

    @Test
    void earliestAvailableStartAfterSaturatedWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 15, 10, 0);
        for (int i = 0; i < service.capacity(CarType.SUV); i++) {
            service.reserve(CarType.SUV, start, 3);
        }

        assertEquals(
                Optional.of(start.plusDays(3)),
                service.earliestAvailableStart(CarType.SUV, start, 2));
    }

    @Test
    void cancelWhileOthersReserveNeverOversells() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        Reservation held = service.reserve(CarType.SUV, start, 3);

        int attempts = 40;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to reserve");
                }
                try {
                    service.reserve(CarType.SUV, start, 3);
                    return true;
                } catch (CarUnavailableException e) {
                    return false;
                }
            });
        }

        long successes = 0;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(pool.submit(task));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            service.cancel(held.id());
            go.countDown();
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes++;
                }
            }
        }

        assertEquals(3, successes);
        assertEquals(3, reservations.findByCarType(CarType.SUV).size());
        assertEquals(3, reservations.count());
    }

    private static void assertDoesNotThrowUuid(String id) {
        UUID.fromString(id);
    }
}
