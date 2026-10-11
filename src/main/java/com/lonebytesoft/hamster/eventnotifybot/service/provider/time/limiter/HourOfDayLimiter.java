package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter;

import java.time.Instant;
import java.time.ZoneId;

public class HourOfDayLimiter implements Limiter {

    private final ZoneId timezone;
    private final int from;
    private final int to;

    public HourOfDayLimiter(
            final ZoneId timezone,
            final int from,
            final int to
    ) {
        this.timezone = timezone;
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean isAllowed(Instant instant) {
        final int hour = instant.atZone(timezone).getHour();
        return (from <= to) && (from <= hour) && (hour <= to)
                || (from > to) && ((from <= hour) || (hour <= to));
    }

    @Override
    public String toString() {
        return "HourOfDayLimiter(from %d to %d)".formatted(from, to);
    }

}
