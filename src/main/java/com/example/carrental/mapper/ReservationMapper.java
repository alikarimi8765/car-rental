package com.example.carrental.mapper;

import com.example.carrental.model.Reservation;
import com.example.carrental.model.entity.ReservationEntity;

import org.springframework.stereotype.Component;

/** Maps between the JPA {@link ReservationEntity} and the domain {@link Reservation}. */
@Component
public class ReservationMapper {

    public ReservationEntity toEntity(Reservation reservation) {
        if (reservation == null) {
            return null;
        }
        ReservationEntity entity = new ReservationEntity();
        entity.setId(reservation.id());
        entity.setCarType(reservation.carType());
        entity.setStart(reservation.start());
        entity.setDays(reservation.days());
        entity.setCustomer(reservation.customer());
        return entity;
    }

    public Reservation toDomain(ReservationEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Reservation(
                entity.getId(),
                entity.getCarType(),
                entity.getStart(),
                entity.getDays(),
                entity.getCustomer());
    }
}
