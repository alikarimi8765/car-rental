package com.example.carrental.service;

import com.example.carrental.model.CarType;

import java.util.function.Supplier;

/**
 * Makes "check availability, then insert" atomic for one car type.
 *
 * <p>An implementation must have committed the work of {@code action} before the next caller
 * for the same type starts. The current implementation is in-process; a database row lock
 * can replace it for several application instances without touching the service.
 */
public interface BookingLock {

    <T> T withLock(CarType type, Supplier<T> action);
}
