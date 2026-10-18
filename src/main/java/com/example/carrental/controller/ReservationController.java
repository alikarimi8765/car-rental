package com.example.carrental.controller;

import com.example.carrental.controller.ApiDtos.BookingRequest;
import com.example.carrental.controller.ApiDtos.NaturalLanguageRequest;
import com.example.carrental.controller.ApiDtos.OutcomeDto;
import com.example.carrental.controller.ApiDtos.ReservationDto;
import com.example.carrental.model.Reservation;
import com.example.carrental.mapper.AssistantResponseMapper;
import com.example.carrental.mapper.BookingCommand;
import com.example.carrental.mapper.BookingRequestMapper;
import com.example.carrental.service.AssistantResponse;
import com.example.carrental.service.CarRentalService;
import com.example.carrental.service.RentalAssistant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP endpoints for creating, listing, and cancelling reservations. */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final RentalAssistant assistant;
    private final CarRentalService service;
    private final AssistantResponseMapper assistantResponseMapper;
    private final BookingRequestMapper bookingRequestMapper;

    public ReservationController(
            RentalAssistant assistant,
            CarRentalService service,
            AssistantResponseMapper assistantResponseMapper,
            BookingRequestMapper bookingRequestMapper) {
        this.assistant = assistant;
        this.service = service;
        this.assistantResponseMapper = assistantResponseMapper;
        this.bookingRequestMapper = bookingRequestMapper;
    }

    @PostMapping("/nl")
    public ResponseEntity<OutcomeDto> reserveFromText(@RequestBody NaturalLanguageRequest request) {
        AssistantResponse response = assistant.handle(request.text(), request.customer());
        return assistantResponseMapper.toHttpResponse(response);
    }

    @PostMapping
    public ResponseEntity<ReservationDto> reserve(@RequestBody BookingRequest request) {
        BookingCommand command = bookingRequestMapper.toCommand(request);
        Reservation reservation = service.reserve(
                command.carType(), command.start(), command.days(), command.customer());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assistantResponseMapper.toReservationDto(reservation));
    }

    @GetMapping
    public List<ReservationDto> list() {
        return service.reservations().stream().map(assistantResponseMapper::toReservationDto).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable("id") String id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
