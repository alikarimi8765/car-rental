package com.example.carrental.mapper;

import com.example.carrental.controller.ApiDtos.OutcomeDto;
import com.example.carrental.controller.ApiDtos.ParsedDto;
import com.example.carrental.controller.ApiDtos.ReservationDto;
import com.example.carrental.model.Reservation;
import com.example.carrental.service.AssistantResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Converts application outcomes into HTTP responses. */
@Component
public class AssistantResponseMapper {

    private static final int UNPROCESSABLE = 422;

    public ResponseEntity<OutcomeDto> toHttpResponse(AssistantResponse response) {
        ParsedDto parsed = ApiMapper.toParsedDto(response.parse());

        return switch (response) {
            case AssistantResponse.Reserved reserved ->
                    response(HttpStatus.CREATED.value(), "RESERVED", reserved.message(), parsed,
                            toReservationDto(reserved.reservation()));
            case AssistantResponse.NeedsClarification clarification ->
                    response(UNPROCESSABLE, "NEEDS_CLARIFICATION", clarification.message(), parsed, null);
            case AssistantResponse.Rejected rejected ->
                    response(statusOf(rejected), "REJECTED", rejected.message(), parsed, null);
        };
    }

    public ReservationDto toReservationDto(Reservation reservation) {
        return ApiMapper.toReservationDto(reservation);
    }

    private static int statusOf(AssistantResponse.Rejected rejected) {
        return rejected.reason() == AssistantResponse.Reason.UNAVAILABLE
                ? HttpStatus.CONFLICT.value()
                : UNPROCESSABLE;
    }

    private static ResponseEntity<OutcomeDto> response(
            int status,
            String outcome,
            String message,
            ParsedDto parsed,
            ReservationDto reservation) {
        return ResponseEntity.status(status)
                .body(new OutcomeDto(outcome, message, parsed, reservation));
    }
}
