package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class AwsLambdaRequestHandler implements RequestHandler<Void, Void> {

    private static final int PROVISIONED_RCU = 20;
    private static final int PROVISIONED_WCU = 20;

    private static final Duration EXECUTION_BUFFER = Duration.ofSeconds(2);
    private static final Duration CYCLE_PAUSE = Duration.ofSeconds(1);
    private static final Duration CYCLE_DURATION = Duration.ofSeconds(2);

    private static final ApplicationContext applicationContext = new ApplicationContext(
            // reads happen only once every minute (see lambda scheduled invocation rate and ApplicationContext#initialize), but the limit is per second
            PROVISIONED_RCU * ((int) TimeUnit.MINUTES.toSeconds(1)),
            // an arbitrary number of writes can happen in a cycle run, worst case its run time tends to zero, see also #CYCLE_PAUSE; the limit is per second
            PROVISIONED_WCU * ((int) CYCLE_PAUSE.toSeconds())
    );

    @Override
    public Void handleRequest(Void input, Context context) {
        applicationContext.initialize();

        new LambdaHandler(
                new EventNotifyBotJobHandler(applicationContext),
                EXECUTION_BUFFER,
                CYCLE_PAUSE,
                CYCLE_DURATION
        ).run(context);
        return null;
    }

}
