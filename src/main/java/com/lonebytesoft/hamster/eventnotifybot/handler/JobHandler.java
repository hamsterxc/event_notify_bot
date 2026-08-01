package com.lonebytesoft.hamster.eventnotifybot.handler;

import java.time.Duration;

/**
 * An individual job execution handler.
 */
public interface JobHandler {

    /**
     * Handle an individual job execution.
     * @param extraTimeout the amount of time the execution is allowed to spend freely besides its unavoidable needs
     */
    void run(Duration extraTimeout) throws Exception;

}
