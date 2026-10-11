package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.scheduler;

import com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter.Limiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

public class RandomRateScheduler extends AbstractSteppingScheduler {

    private static final Logger log = LoggerFactory.getLogger(RandomRateScheduler.class);

    private final long rateSeconds;
    private final double variance;

    public RandomRateScheduler(
            final Duration rate,
            final double variance,
            final Collection<Limiter> limiters,
            final Duration maxDelta
    ) {
        super(limiters, maxDelta);
        if (!limiters.isEmpty() && (maxDelta == null)) {
            log.warn("Limiters present but the maximum delta is not set - possible infinite loop: rate {}, variance {}, limiters {}", rate, variance, limiters);
        }

        this.rateSeconds = rate.getSeconds();
        this.variance = variance;
    }

    public RandomRateScheduler(
            final Duration rate,
            final double variance
    ) {
        this(rate, variance, List.of(), null);
    }

    @Override
    protected Duration getStep() {
        final double multiplier = 1.0 + (Math.random() * 2.0 * variance - variance); // 1 +- variance
        return Duration.ofSeconds((long) (rateSeconds * multiplier));
    }

}
