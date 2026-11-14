package com.example.carrental.service;

import com.example.carrental.model.CarType;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One in-process lock per car type, held until the transaction has committed.
 *
 * <p>A {@link TransactionTemplate} is used instead of {@code @Transactional} so the commit
 * happens before the lock is released; with the annotation it would happen after the method
 * returns, possibly after the lock is released. Bookings of different types do not block
 * each other. The lock does not coordinate several application processes.
 */
@Component
public class TransactionalBookingLock implements BookingLock {

    private final Map<CarType, ReentrantLock> locks = new EnumMap<>(CarType.class);
    private final TransactionTemplate transactions;

    public TransactionalBookingLock(TransactionTemplate transactions) {
        this.transactions = transactions;
        for (CarType type : CarType.values()) {
            locks.put(type, new ReentrantLock());
        }
    }

    @Override
    public <T> T withLock(CarType type, Supplier<T> action) {
        ReentrantLock lock = locks.get(type);
        lock.lock();
        try {
            return transactions.execute(status -> action.get());
        } finally {
            lock.unlock();
        }
    }
}
