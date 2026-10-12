package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.scheduler;

import java.time.Instant;

public class DisabledScheduler implements Scheduler {

    @Override
    public Instant getNext(Instant instant) {
        return null;
    }

}
