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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RandomRateSchedulerTest {

    private static final ZoneId TIMEZONE = ZoneId.of("UTC");

    @Test
    public void noLimiters_oneStep() {
        final Scheduler scheduler = new RandomRateScheduler(Duration.ofHours(1), 0.5);
        final Instant next = scheduler.getNext(Instant.parse("2026-01-01T00:15:00Z")); // somewhere within 01:15 +- (1 hour * 0.5), i.e. from 00:45 to 01:45
        assertTrue(next.compareTo(Instant.parse("2026-01-01T00:45:00Z")) >= 0);
        assertTrue(next.compareTo(Instant.parse("2026-01-01T01:45:00Z")) <= 0);
    }

    @Test
    public void hourLimiters_stepUntilWithinLimits() {
        final Scheduler scheduler = new RandomRateScheduler(
                Duration.ofHours(1),
                0.5,
                List.of(new HourOfDayLimiter(TIMEZONE, 6, 22)),
                null
        );
        final Instant next = scheduler.getNext(Instant.parse("2026-01-01T00:15:00Z")); // somewhere between 06:00 (earliest time within limits) and 07:30 (latest time outside limits plus maximum possible delta)
        assertTrue(next.compareTo(Instant.parse("2026-01-01T06:00:00Z")) >= 0);
        assertTrue(next.compareTo(Instant.parse("2026-01-01T07:30:00Z")) < 0);
    }

    @Test
    public void impossibleLimiters_noNext() {
        final Scheduler scheduler = new RandomRateScheduler(
                Duration.ofHours(1),
                0.5,
                List.of(new DayOfWeekLimiter(TIMEZONE, EnumSet.noneOf(DayOfWeek.class))),
                Duration.ofDays(1)
        );
        assertNull(
                scheduler.getNext(Instant.parse("2026-01-01T00:15:00Z"))
        );
    }

    @Test
    public void unreachableLimiters_noNext() {
        final Scheduler scheduler = new RandomRateScheduler(
                Duration.ofHours(1),
                0.5,
                List.of(new DayOfWeekLimiter(TIMEZONE, EnumSet.of(DayOfWeek.SUNDAY))),
                Duration.ofDays(1)
        );
        assertNull(
                scheduler.getNext(Instant.parse("2026-01-01T00:15:00Z")) // Thursday
        );
    }

}
