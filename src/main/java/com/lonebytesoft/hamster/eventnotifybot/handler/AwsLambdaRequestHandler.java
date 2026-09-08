package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.time.Duration;

@SuppressWarnings("unused")
public class AwsLambdaRequestHandler implements RequestHandler<Void, Void> {

    private static final Duration EXECUTION_BUFFER = Duration.ofSeconds(2);
    private static final Duration CYCLE_PAUSE = Duration.ofSeconds(1);
    private static final Duration CYCLE_DURATION = Duration.ofSeconds(2);

    @Override
    public Void handleRequest(Void input, Context context) {
        new LambdaHandler(
                new EventNotifyBotJobHandler(),
                EXECUTION_BUFFER,
                CYCLE_PAUSE,
                CYCLE_DURATION
        ).run(context);
        return null;
    }

}
