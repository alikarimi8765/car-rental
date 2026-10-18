package com.example.carrental.service;

import com.example.carrental.model.CarType;
import com.example.carrental.model.Reservation;
import com.example.carrental.exception.CarUnavailableException;
import com.example.carrental.exception.InvalidReservationException;
import com.example.carrental.nl.ParseResult;
import com.example.carrental.nl.ReservationParser;
import com.example.carrental.util.HumanDates;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Free text in, reservation (or an explanation of why not) out.
 *
 * <p>A reservation is only attempted when the parse is <em>complete and
 * issue-free</em>; anything less is sent back as a question rather than
 * being guessed at.
 */
@Service
public final class RentalAssistant {

    private final CarRentalService service;
    private final ReservationParser parser;

    public RentalAssistant(CarRentalService service, ReservationParser parser) {
        this.service = Objects.requireNonNull(service, "service");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public AssistantResponse handle(String text) {
        return handle(text, null);
    }

    public AssistantResponse handle(String text, String customer) {
        ParseResult parsed = parser.parse(text);
        if (!parsed.isComplete()) {
            return new AssistantResponse.NeedsClarification(clarification(parsed), parsed);
        }

        CarType type = parsed.carType();
        LocalDateTime start = parsed.start().orElseThrow();
        int days = parsed.durationDays();
        try {
            Reservation reservation = service.reserve(type, start, days, customer);
            return new AssistantResponse.Reserved(reservation, confirmation(reservation), parsed);
        } catch (CarUnavailableException e) {
            return new AssistantResponse.Rejected(
                    AssistantResponse.Reason.UNAVAILABLE, unavailable(e, days), parsed);
        } catch (InvalidReservationException e) {
            return new AssistantResponse.Rejected(AssistantResponse.Reason.INVALID, e.getMessage(), parsed);
        }
    }

    private static String confirmation(Reservation r) {
        return "Reserved %s %s: %s until %s (%s).".formatted(
                r.carType().label(), r.id(), HumanDates.moment(r.start()), HumanDates.moment(r.end()), days(r.days()));
    }

    private static String clarification(ParseResult parsed) {
        return "I couldn't complete the reservation yet:\n" + parsed.issues().stream()
                .map(issue -> "- " + issue.message())
                .collect(Collectors.joining("\n"));
    }

    private String unavailable(CarUnavailableException e, int days) {
        String label = e.carType().label();
        if (e.capacity() == 0) {
            return "We have no %s in the fleet.".formatted(label);
        }
        String message = "No %s is free from %s for %s.".formatted(label, HumanDates.moment(e.start()), days(days));
        return service.earliestAvailableStart(e.carType(), e.start(), days)
                .map(alternative -> message + " The earliest start that works is " + HumanDates.moment(alternative) + ".")
                .orElse(message);
    }

    private static String days(int days) {
        return days + (days == 1 ? " day" : " days");
    }
}
