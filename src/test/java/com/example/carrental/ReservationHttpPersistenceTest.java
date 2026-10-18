package com.example.carrental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.carrental.model.CarType;
import com.example.carrental.repository.ReservationRepository;
import com.example.carrental.service.CarRentalService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** POST/DELETE through the running application against the test H2 database. */
@SpringBootTest
class ReservationHttpPersistenceTest {

    private static final Pattern ID_IN_JSON = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return TestClocks.fixed(LocalDateTime.of(2026, 10, 8, 17, 49));
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    ReservationRepository reservations;

    @Autowired
    CarRentalService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void structuredPostAndDeletePersistInH2() throws Exception {
        MvcResult created = mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carType\":\"suv\",\"start\":\"2026-10-15T10:00\",\"days\":3,\"customer\":\"Ada\"}"))
                .andReturn();

        assertEquals(201, created.getResponse().getStatus());
        String body = created.getResponse().getContentAsString();
        String id = extractId(body);
        UUID.fromString(id);
        assertEquals(1, reservations.count());
        assertEquals("Ada", reservations.findById(id).orElseThrow().getCustomer());

        int status = mvc.perform(delete("/api/reservations/" + id)).andReturn().getResponse().getStatus();
        assertEquals(204, status);
        assertEquals(0, reservations.count());
        assertTrue(reservations.findById(id).isEmpty());
    }

    @Test
    void naturalLanguageBookReturns201() throws Exception {
        MvcResult result = mvc.perform(post("/api/reservations/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Reserve an SUV from 15 October 2026 at 10:00 for 3 days\",\"customer\":\"Ada\"}"))
                .andReturn();

        assertEquals(201, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("RESERVED"));
        assertEquals(1, reservations.count());
    }

    @Test
    void naturalLanguageUnclearReturns422() throws Exception {
        MvcResult result = mvc.perform(post("/api/reservations/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Reserve a car\"}"))
                .andReturn();

        assertEquals(422, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("NEEDS_CLARIFICATION"));
        assertEquals(0, reservations.count());
    }

    @Test
    void naturalLanguageFleetFullReturns409() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 15, 10, 0);
        for (int i = 0; i < service.capacity(CarType.SUV); i++) {
            service.reserve(CarType.SUV, start, 3);
        }

        MvcResult result = mvc.perform(post("/api/reservations/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Reserve an SUV from 15 October 2026 at 10:00 for 3 days\"}"))
                .andReturn();

        assertEquals(409, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("REJECTED"));
        assertEquals(3, reservations.count());
    }

    @Test
    void cancelUnknownIdReturns404() throws Exception {
        int status = mvc.perform(delete("/api/reservations/" + UUID.randomUUID()))
                .andReturn()
                .getResponse()
                .getStatus();
        assertEquals(404, status);
    }

    @Test
    void structuredPostInvalidTypeReturns422() throws Exception {
        MvcResult result = mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carType\":\"truck\",\"start\":\"2026-10-15T10:00\",\"days\":3}"))
                .andReturn();

        assertEquals(422, result.getResponse().getStatus());
        assertEquals(0, reservations.count());
    }

    @Test
    void structuredPostZeroDaysReturns422() throws Exception {
        MvcResult result = mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carType\":\"suv\",\"start\":\"2026-10-15T10:00\",\"days\":0}"))
                .andReturn();

        assertEquals(422, result.getResponse().getStatus());
        assertEquals(0, reservations.count());
    }

    @Test
    void listReturnsPersistedReservations() throws Exception {
        mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carType\":\"van\",\"start\":\"2026-10-15T10:00\",\"days\":2,\"customer\":\"Bob\"}"))
                .andReturn();

        MvcResult listed = mvc.perform(get("/api/reservations")).andReturn();
        assertEquals(200, listed.getResponse().getStatus());
        String body = listed.getResponse().getContentAsString();
        assertTrue(body.contains("van") || body.contains("VAN"));
        assertTrue(body.contains("Bob"));
    }

    private static String extractId(String body) {
        Matcher matcher = ID_IN_JSON.matcher(body);
        assertTrue(matcher.find(), body);
        return matcher.group(1);
    }
}
