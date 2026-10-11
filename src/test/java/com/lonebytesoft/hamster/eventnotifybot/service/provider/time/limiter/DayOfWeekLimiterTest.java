package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DayOfWeekLimiterTest {

    private static final ZoneId TIMEZONE = ZoneId.of("UTC");

    @Test
    public void emptyDays_noneAllowed() {
        final Limiter limiter = new DayOfWeekLimiter(TIMEZONE, EnumSet.noneOf(DayOfWeek.class));
        final Instant base = Instant.parse("2026-01-01T00:00:00Z");
        IntStream.range(0, 7).forEach(days -> {
            final Instant instant = base.plus(days, ChronoUnit.DAYS);
            assertFalse(limiter.isAllowed(instant));
        });
    }

    @Test
    public void allDays_allAllowed() {
        final Limiter limiter = new DayOfWeekLimiter(TIMEZONE, EnumSet.allOf(DayOfWeek.class));
        final Instant base = Instant.parse("2026-01-01T00:00:00Z");
        IntStream.range(0, 7).forEach(days -> {
            final Instant instant = base.plus(days, ChronoUnit.DAYS);
            assertTrue(limiter.isAllowed(instant));
        });
    }

    @Test
    public void someDaysAllowed() {
        final Limiter limiter = new DayOfWeekLimiter(TIMEZONE, EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        final Instant base = Instant.parse("2026-01-01T00:00:00Z"); // Thursday
        Map.of(
                0, false, // Thursday
                1, false, // Friday
                2, true, // Saturday
                3, true, // Sunday
                4, false, // Monday
                5, false, // Tuesday
                6, false // Wednesday
        ).forEach((days, isAllowed) -> {
            final Instant instant = base.plus(days, ChronoUnit.DAYS);
            assertEquals(isAllowed, limiter.isAllowed(instant));
        });
    }

}
