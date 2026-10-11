package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.scheduler;

import com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter.Limiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class FixedRateScheduler implements Scheduler {

    private static final Logger log = LoggerFactory.getLogger(FixedRateScheduler.class);

    private final Duration rate;
    private final Collection<Limiter> limiters;
    private final Duration maxDelta;

    public FixedRateScheduler(
            final Duration rate,
            final Collection<Limiter> limiters,
            final Duration maxDelta
    ) {
        this.rate = rate;
        this.limiters = limiters;
        this.maxDelta = maxDelta;
        if (!limiters.isEmpty() && (maxDelta == null)) {
            log.warn("Limiters present but the maximum delta is not set - possible infinite loop: rate {}, limiters {}", rate, limiters);
        }
    }

    public FixedRateScheduler(
            final Duration rate
    ) {
        this(rate, List.of(), null);
    }

    @Override
    public Instant getNext(Instant instant) {
        Duration delta = Duration.ZERO;
        do {
            delta = delta.plus(rate);
            final Instant next = instant.plus(delta);
            if (limiters.stream().allMatch(limiter -> limiter.isAllowed(next))) {
                return next;
            }
        } while ((maxDelta == null) || (delta.compareTo(maxDelta) <= 0));
        return null;
    }

}
