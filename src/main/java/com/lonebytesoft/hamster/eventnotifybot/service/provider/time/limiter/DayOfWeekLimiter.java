package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.stream.Collectors;

public class DayOfWeekLimiter implements Limiter {

    private final ZoneId timezone;
    private final Collection<DayOfWeek> days;

    public DayOfWeekLimiter(
            final ZoneId timezone,
            final Collection<DayOfWeek> days
    ) {
        this.timezone = timezone;
        if ((days == null) || days.isEmpty()) {
            this.days = EnumSet.noneOf(DayOfWeek.class);
        } else {
            this.days = days instanceof EnumSet ? days : EnumSet.copyOf(days);
        }
    }

    @Override
    public boolean isAllowed(Instant instant) {
        final DayOfWeek dayOfWeek = instant.atZone(timezone).getDayOfWeek();
        return days.contains(dayOfWeek);
    }

    @Override
    public String toString() {
        return "DayOfWeekLimiter(%s)".formatted(
                days
                        .stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(", "))
        );
    }

}
