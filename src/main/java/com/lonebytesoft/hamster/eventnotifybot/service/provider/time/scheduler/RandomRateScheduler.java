package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.scheduler;

import com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter.Limiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class RandomRateScheduler implements Scheduler {

    private static final Logger log = LoggerFactory.getLogger(RandomRateScheduler.class);

    private final long rateSeconds;
    private final double variance;
    private final Collection<Limiter> limiters;
    private final Duration maxDelta;

    public RandomRateScheduler(
            final Duration rate,
            final double variance,
            final Collection<Limiter> limiters,
            final Duration maxDelta
    ) {
        this.rateSeconds = rate.getSeconds();
        this.variance = variance;
        this.limiters = limiters;
        this.maxDelta = maxDelta;
        if (!limiters.isEmpty() && (maxDelta == null)) {
            log.warn("Limiters present but the maximum delta is not set - possible infinite loop: rate {}, variance {}, limiters {}", rate, variance, limiters);
        }
    }

    public RandomRateScheduler(
            final Duration rate,
            final double variance
    ) {
        this(rate, variance, List.of(), null);
    }

    @Override
    public Instant getNext(Instant instant) {
        Duration delta = Duration.ZERO;
        do {
            final double multiplier = 1.0 + (Math.random() * 2.0 * variance - variance); // 1 +- variance
            delta = delta.plusSeconds((long) (rateSeconds * multiplier));
            final Instant next = instant.plus(delta);
            if (limiters.stream().allMatch(limiter -> limiter.isAllowed(next))) {
                return next;
            }
        } while ((maxDelta == null) || (delta.compareTo(maxDelta) <= 0));
        return null;
    }

}
