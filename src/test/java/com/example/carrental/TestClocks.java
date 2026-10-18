package com.example.carrental;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Deterministic clocks, so tests never depend on the real date. */
public final class TestClocks {

    private TestClocks() {
    }

    /** A clock for which {@code LocalDateTime.now(clock)} is always {@code moment}. */
    public static Clock fixed(LocalDateTime moment) {
        return Clock.fixed(moment.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
