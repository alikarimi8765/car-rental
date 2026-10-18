package com.example.carrental.repository;

import com.example.carrental.model.CarType;
import com.example.carrental.model.entity.FleetCapacityEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FleetCapacityRepository extends JpaRepository<FleetCapacityEntity, CarType> {
}
