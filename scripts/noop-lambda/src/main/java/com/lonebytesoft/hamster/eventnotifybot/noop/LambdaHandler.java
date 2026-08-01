package com.lonebytesoft.hamster.eventnotifybot.noop;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class LambdaHandler implements RequestHandler<Void, Void> {

    @Override
    public Void handleRequest(Void input, Context context) {
        return null;
    }

}
