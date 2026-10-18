package com.example.carrental.model.entity;

import com.example.carrental.model.CarType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** How many cars of one type the company owns. Source of truth for fleet capacity. */
@Entity
@Table(name = "fleet_capacity")
public class FleetCapacityEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private CarType carType;

    @Column(nullable = false)
    private int size;

    public FleetCapacityEntity() {
    }

    public CarType getCarType() {
        return carType;
    }

    public void setCarType(CarType carType) {
        this.carType = carType;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
