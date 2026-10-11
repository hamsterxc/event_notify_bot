package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.scheduler;

import com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter.Limiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

public class FixedRateScheduler extends AbstractSteppingScheduler {

    private static final Logger log = LoggerFactory.getLogger(FixedRateScheduler.class);

    private final Duration rate;

    public FixedRateScheduler(
            final Duration rate,
            final Collection<Limiter> limiters,
            final Duration maxDelta
    ) {
        super(limiters, maxDelta);
        if (!limiters.isEmpty() && (maxDelta == null)) {
            log.warn("Limiters present but the maximum delta is not set - possible infinite loop: rate {}, limiters {}", rate, limiters);
        }

        this.rate = rate;
    }

    public FixedRateScheduler(
            final Duration rate
    ) {
        this(rate, List.of(), null);
    }

    @Override
    protected Duration getStep() {
        return rate;
    }

}
