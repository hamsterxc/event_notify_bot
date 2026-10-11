package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HourOfDayLimiterTest {

    private static final ZoneId TIMEZONE = ZoneId.of("UTC");

    @Test
    public void fromToNaturalOrder_hourBelowFrom() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 6, 22);
        assertFalse(limiter.isAllowed(Instant.parse("2026-01-01T05:59:59Z")));
    }

    @Test
    public void fromToNaturalOrder_hourEqualFrom() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 6, 22);
        assertTrue(limiter.isAllowed(Instant.parse("2026-01-01T06:00:00Z")));
    }

    @Test
    public void fromToNaturalOrder_hourInside() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 6, 22);
        assertTrue(limiter.isAllowed(Instant.parse("2026-01-01T12:00:00Z")));
    }

    @Test
    public void fromToNaturalOrder_hourEqualTo() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 6, 22);
        assertTrue(limiter.isAllowed(Instant.parse("2026-01-01T22:59:59Z")));
    }

    @Test
    public void fromToNaturalOrder_hourAboveTo() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 6, 22);
        assertFalse(limiter.isAllowed(Instant.parse("2026-01-01T23:00:00Z")));
    }

    @Test
    public void fromToInverseOrder_midnight() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 22, 6);
        assertTrue(limiter.isAllowed(Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    public void fromToInverseOrder_hourBelowTo() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 22, 6);
        assertTrue(limiter.isAllowed(Instant.parse("2026-01-01T04:00:00Z")));
    }

    @Test
    public void fromToInverseOrder_hourEqualTo() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 22, 6);
        assertTrue(limiter.isAllowed(Instant.parse("2026-01-01T06:59:59Z")));
    }

    @Test
    public void fromToInverseOrder_hourOutside() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 22, 6);
        assertFalse(limiter.isAllowed(Instant.parse("2026-01-01T12:00:00Z")));
    }

    @Test
    public void fromToInverseOrder_hourEqualFrom() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 22, 6);
        assertTrue(limiter.isAllowed(Instant.parse("2026-01-01T22:00:00Z")));
    }

    @Test
    public void fromToInverseOrder_hourAboveFrom() {
        final Limiter limiter = new HourOfDayLimiter(TIMEZONE, 22, 6);
        assertTrue(limiter.isAllowed(Instant.parse("2026-01-01T23:00:00Z")));
    }

}
