package com.example.carrental.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FleetTest {

    @Test
    void fromRequiresEveryCarType() {
        Fleet fleet = Fleet.from(Map.of(CarType.SEDAN, 5, CarType.SUV, 3, CarType.VAN, 2));
        assertEquals(5, fleet.size(CarType.SEDAN));
        assertEquals(3, fleet.size(CarType.SUV));
        assertEquals(2, fleet.size(CarType.VAN));
    }

    @Test
    void missingTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Fleet.from(Map.of(CarType.SUV, 3)));
    }

    @Test
    void explicitZeroDisablesType() {
        Fleet fleet = Fleet.from(Map.of(CarType.SEDAN, 5, CarType.SUV, 3, CarType.VAN, 0));
        assertEquals(0, fleet.size(CarType.VAN));
    }

    @Test
    void negativeSizeIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Fleet.from(Map.of(CarType.SEDAN, -1, CarType.SUV, 3, CarType.VAN, 2)));
    }

    @Test
    void nullSizeIsRejected() {
        var sizes = new EnumMap<CarType, Integer>(CarType.class);
        sizes.put(CarType.SEDAN, 5);
        sizes.put(CarType.SUV, null);
        sizes.put(CarType.VAN, 2);
        assertThrows(IllegalArgumentException.class, () -> Fleet.from(sizes));
    }
}
