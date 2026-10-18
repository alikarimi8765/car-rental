package com.example.carrental;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.carrental.model.CarType;
import com.example.carrental.model.Reservation;
import com.example.carrental.service.CarRentalService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bookings in a file H2 database survive a full application restart.
 * Uses a temp file under the test temp directory — never {@code ./data/carrental}.
 */
class ReservationRestartTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 17, 49);
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 15, 10, 0);

    @TempDir
    Path tempDir;

    @Test
    void bookingsSurviveApplicationRestart() {
        String dbPath = tempDir.resolve("carrental").toAbsolutePath().toString();
        String jdbcUrl = "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=-1";
        String bookedId;

        try (ConfigurableApplicationContext first = startApp(jdbcUrl)) {
            CarRentalService service = first.getBean(CarRentalService.class);
            Reservation booked = service.reserve(CarType.SUV, START, 3, "Ada");
            bookedId = booked.id();
            assertEquals(1, service.reservations().size());
        }

        try (ConfigurableApplicationContext second = startApp(jdbcUrl)) {
            CarRentalService service = second.getBean(CarRentalService.class);
            List<Reservation> reservations = service.reservations();
            assertEquals(1, reservations.size());
            Reservation restored = reservations.getFirst();
            assertEquals(bookedId, restored.id());
            assertEquals(CarType.SUV, restored.carType());
            assertEquals(START, restored.start());
            assertEquals(3, restored.days());
            assertEquals("Ada", restored.customer());
        }
    }

    private static ConfigurableApplicationContext startApp(String jdbcUrl) {
        return new SpringApplicationBuilder(CarRentalApplication.class, FixedClockConfiguration.class)
                .run(
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--spring.main.web-application-type=none",
                        "--spring.main.banner-mode=off",
                        "--spring.main.allow-bean-definition-overriding=true");
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return TestClocks.fixed(NOW);
        }
    }
}
