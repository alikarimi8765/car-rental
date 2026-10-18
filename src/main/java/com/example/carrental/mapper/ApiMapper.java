package com.example.carrental.mapper;

import com.example.carrental.controller.ApiDtos.IssueDto;
import com.example.carrental.controller.ApiDtos.ParsedDto;
import com.example.carrental.controller.ApiDtos.ReservationDto;
import com.example.carrental.model.CarType;
import com.example.carrental.model.Reservation;
import com.example.carrental.nl.Issue;
import com.example.carrental.nl.ParseResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/** Shared API DTO mapping helpers. */
public final class ApiMapper {

    private ApiMapper() {
    }

    public static ReservationDto toReservationDto(Reservation reservation) {
        if (reservation == null) {
            return null;
        }
        return new ReservationDto(
                reservation.id(),
                carTypeLabel(reservation.carType()),
                dateTimeToString(reservation.start()),
                reservation.end().toString(),
                reservation.days(),
                reservation.customer());
    }

    public static IssueDto toIssueDto(Issue issue) {
        if (issue == null) {
            return null;
        }
        return new IssueDto(
                issue.kind() == null ? null : issue.kind().name(),
                issue.field() == null ? null : issue.field().name(),
                issue.message());
    }

    public static ParsedDto toParsedDto(ParseResult result) {
        if (result == null) {
            return null;
        }
        List<IssueDto> issues = result.issues() == null
                ? null
                : result.issues().stream().map(ApiMapper::toIssueDto).toList();
        return new ParsedDto(
                carTypeLabel(result.carType()),
                result.start().map(LocalDateTime::toString).orElse(null),
                localDateToString(result.startDate()),
                localTimeToString(result.startTime()),
                result.durationDays(),
                result.isComplete(),
                issues);
    }

    private static String carTypeLabel(CarType carType) {
        return carType == null ? null : carType.name().toLowerCase(Locale.ROOT);
    }

    private static String dateTimeToString(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static String localDateToString(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static String localTimeToString(LocalTime value) {
        return value == null ? null : value.toString();
    }
}
