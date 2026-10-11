package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.scheduler;

import java.time.Instant;

public interface Scheduler {

    Instant getNext(Instant instant);

}
