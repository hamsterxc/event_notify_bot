package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.scheduler;

import com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter.Limiter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

abstract class AbstractSteppingScheduler implements Scheduler {

    private final Collection<Limiter> limiters;
    private final Duration maxDelta;

    protected AbstractSteppingScheduler(
            final Collection<Limiter> limiters,
            final Duration maxDelta
    ) {
        this.limiters = limiters;
        this.maxDelta = maxDelta;
    }

    protected abstract Duration getStep();

    @Override
    public Instant getNext(Instant instant) {
        Duration delta = Duration.ZERO;
        do {
            delta = delta.plus(getStep());
            final Instant next = instant.plus(delta);
            if (limiters.stream().allMatch(limiter -> limiter.isAllowed(next))) {
                return next;
            }
        } while ((maxDelta == null) || (delta.compareTo(maxDelta) <= 0));
        return null;
    }

}
