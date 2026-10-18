package com.example.carrental.mapper;

import com.example.carrental.model.CarType;

import java.time.LocalDateTime;

/** Validated input for a structured reservation request. */
public record BookingCommand(
        CarType carType,
        LocalDateTime start,
        int days,
        String customer) {
}
