package com.example.carrental;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.carrental.exception.CorrelationIdFilter;
import com.example.carrental.service.CarRentalService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Every failure leaves the API as a ProblemDetail with a correlation id, and never a stack trace. */
@SpringBootTest
class ApiErrorHandlingTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    CorrelationIdFilter correlationIdFilter;

    @MockitoBean
    CarRentalService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(correlationIdFilter).build();
    }

    @Test
    void overLongCustomerIsABadRequestNotAServerError() throws Exception {
        String body = "{\"carType\":\"suv\",\"start\":\"2026-10-15T10:00\",\"days\":3,\"customer\":\""
                + "x".repeat(101) + "\"}";
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("customer must be at most 100")))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    @Test
    void overLongNaturalLanguageTextIsABadRequest() throws Exception {
        String body = "{\"text\":\"" + "a".repeat(501) + "\"}";
        mvc.perform(post("/api/reservations/nl").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("text must be at most 500")));
    }

    @Test
    void missingNaturalLanguageTextIsABadRequest() throws Exception {
        mvc.perform(post("/api/reservations/nl").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("text is required")));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("not valid JSON")));
    }

    @Test
    void emptyBodyIsABadRequest() throws Exception {
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void springOwnErrorsKeepTheirStatus() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get("/api/reservations/nl")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unexpectedExceptionIsAGeneric500WithTheCorrelationId() throws Exception {
        when(service.reservations()).thenThrow(new IllegalStateException("secret internal detail"));

        mvc.perform(get("/api/reservations").header(CorrelationIdFilter.HEADER, "abc-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"))
                .andExpect(jsonPath("$.detail", not(containsString("secret"))));
    }

    @Test
    void unsafeCorrelationIdFromTheCallerIsReplaced() throws Exception {
        when(service.reservations()).thenReturn(java.util.List.of());

        mvc.perform(get("/api/reservations").header(CorrelationIdFilter.HEADER, "bad id\twith spaces"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, not(containsString(" "))));
    }
}
