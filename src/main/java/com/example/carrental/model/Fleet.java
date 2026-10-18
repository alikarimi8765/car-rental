package com.example.carrental.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * How many cars of each type the company owns.
 *
 * <p>Built once at startup from the {@code fleet_capacity} table — not from
 * hardcoded defaults in this class.
 */
public record Fleet(Map<CarType, Integer> capacity) {

    /**
     * Builds a fleet from a complete capacity map (every {@link CarType} must be present).
     * An explicit {@code 0} means no cars of that type.
     */
    public static Fleet from(Map<CarType, Integer> sizes) {
        Objects.requireNonNull(sizes, "fleet");
        var capacity = new EnumMap<CarType, Integer>(CarType.class);
        for (var type : CarType.values()) {
            Integer size = sizes.get(type);
            if (size == null || size < 0) {
                throw new IllegalArgumentException(
                        "Fleet size for %s must be present and non-negative".formatted(type.label()));
            }
            capacity.put(type, size);
        }
        return new Fleet(Map.copyOf(capacity));
    }

    public int size(CarType type) {
        return capacity.get(type);
    }
}
