package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.amazonaws.services.lambda.runtime.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@SuppressWarnings("unused")
public class LambdaHandler {

    private static final Logger log = LoggerFactory.getLogger(LambdaHandler.class);

    /**
     * Time window to leave between the end of cycles execution and global lambda timeout.
     */
    private final Duration executionBuffer;
    /**
     * Pause between cycles.
     */
    private final long cyclePauseMillis;
    /**
     * Approximate upper bound of one cycle execution time (excluding pause and arbitrary timeouts).
     */
    private final long cycleDurationMillis;

    public LambdaHandler(
            final Duration executionBuffer,
            final Duration cyclePause,
            final Duration cycleDuration
    ) {
        this.executionBuffer = executionBuffer;
        this.cyclePauseMillis = cyclePause.toMillis();
        this.cycleDurationMillis = cycleDuration.toMillis();
    }

    public void run(final Context context) {
        Stopwatch stopwatch = new Stopwatch(context, executionBuffer);

        final JobHandler jobHandler = JobHandlerFactory.createJobHandler();
        stopwatch = stopwatch.tick(context);
        log.debug("Initialized in {} ms", stopwatch.duration());

        boolean isFirstCycle = true;
        while (stopwatch.remaining() > (isFirstCycle ? cycleDurationMillis : cycleDurationMillis + cyclePauseMillis)) {
            if (isFirstCycle) {
                isFirstCycle = false;
            } else {
                try {
                    Thread.sleep(cyclePauseMillis);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for {} ms", cyclePauseMillis, e);
                    Thread.currentThread().interrupt();
                    return;
                }
                stopwatch = stopwatch.tick(context);
            }

            try {
                jobHandler.run(Duration.ofMillis(Math.max(0, stopwatch.remaining() - cycleDurationMillis)));
            } catch (Exception e) {
                log.error("Error while running cycle", e);
                return;
            }
            stopwatch = stopwatch.tick(context);
            log.debug("Ran cycle in {} ms", stopwatch.duration());
        }
    }

    private record Stopwatch(
            long duration,
            long remaining,
            long remainingBuffer
    ) {

        public Stopwatch(
                final Context context,
                final Duration remainingBuffer
        ) {
            final long remainingBufferMillis = remainingBuffer.toMillis();
            this(0, remaining(context, remainingBufferMillis), remainingBufferMillis);
        }

        public Stopwatch tick(final Context context) {
            final long remaining = remaining(context, remainingBuffer);
            return new Stopwatch(remaining() - remaining, remaining, remainingBuffer);
        }

        private static long remaining(
                final Context context,
                final long remainingBuffer
        ) {
            return context.getRemainingTimeInMillis() - remainingBuffer;
        }

    }

}
