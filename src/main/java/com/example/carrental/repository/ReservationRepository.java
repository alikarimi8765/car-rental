package com.example.carrental.repository;

import com.example.carrental.model.CarType;
import com.example.carrental.model.entity.ReservationEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<ReservationEntity, String> {

    List<ReservationEntity> findByCarType(CarType carType);
}
