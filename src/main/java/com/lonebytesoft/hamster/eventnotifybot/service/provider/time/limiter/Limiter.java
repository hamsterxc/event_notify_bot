package com.lonebytesoft.hamster.eventnotifybot.service.provider.time.limiter;

import java.time.Instant;

public interface Limiter {

    boolean isAllowed(Instant instant);

}
