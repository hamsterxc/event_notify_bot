package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.scheduler;

import com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter.DayOfWeekLimiter;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter.HourOfDayLimiter;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FixedRateSchedulerTest {

    private static final ZoneId TIMEZONE = ZoneId.of("UTC");

    @Test
    public void noLimiters_oneStep() {
        final Scheduler scheduler = new FixedRateScheduler(Duration.ofHours(1));
        assertEquals(
                Instant.parse("2026-01-01T01:15:00Z"),
                scheduler.getNext(Instant.parse("2026-01-01T00:15:00Z"))
        );
    }

    @Test
    public void hourLimiters_stepUntilWithinLimits() {
        final Scheduler scheduler = new FixedRateScheduler(
                Duration.ofHours(1),
                List.of(new HourOfDayLimiter(TIMEZONE, 6, 22)),
                null
        );
        assertEquals(
                Instant.parse("2026-01-01T06:15:00Z"),
                scheduler.getNext(Instant.parse("2026-01-01T00:15:00Z"))
        );
    }

    @Test
    public void impossibleLimiters_noNext() {
        final Scheduler scheduler = new FixedRateScheduler(
                Duration.ofHours(1),
                List.of(new DayOfWeekLimiter(TIMEZONE, EnumSet.noneOf(DayOfWeek.class))),
                Duration.ofDays(1)
        );
        assertNull(
                scheduler.getNext(Instant.parse("2026-01-01T00:15:00Z"))
        );
    }

    @Test
    public void unreachableLimiters_noNext() {
        final Scheduler scheduler = new FixedRateScheduler(
                Duration.ofHours(1),
                List.of(new DayOfWeekLimiter(TIMEZONE, EnumSet.of(DayOfWeek.SUNDAY))),
                Duration.ofDays(1)
        );
        assertNull(
                scheduler.getNext(Instant.parse("2026-01-01T00:15:00Z")) // Thursday
        );
    }

}
