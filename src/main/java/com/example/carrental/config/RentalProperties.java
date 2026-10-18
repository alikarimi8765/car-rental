package com.example.carrental.config;

import com.example.carrental.model.CarType;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * {@code rental.fleet.<type>=<count>} seeds the {@code fleet_capacity} table when it is empty,
 * and {@code rental.max-rental-days}. After seeding, the database is the source of truth and
 * is loaded once into the {@code Fleet} bean at startup. Set {@code 0} to disable a type.
 */
@ConfigurationProperties(prefix = "rental")
public record RentalProperties(Map<CarType, Integer> fleet, @DefaultValue("30") int maxRentalDays) {
}
