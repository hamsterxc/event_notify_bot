package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.time.Duration;

@SuppressWarnings("unused")
public class AwsLambdaRequestHandler implements RequestHandler<Void, Void> {

    @Override
    public Void handleRequest(Void input, Context context) {
        new LambdaHandler(
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2)
        ).run(context);
        return null;
    }

}
