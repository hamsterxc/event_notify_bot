package com.lonebytesoft.hamster.eventnotifybot;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.lonebytesoft.hamster.eventnotifybot.handler.EventNotifyBotJobHandler;
import com.lonebytesoft.hamster.eventnotifybot.handler.LambdaHandler;

import java.time.Duration;

public class LocalRun {

    private static final Duration GLOBAL_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration EXECUTION_BUFFER = Duration.ofSeconds(2);
    private static final Duration CYCLE_PAUSE = Duration.ofSeconds(1);
    private static final Duration CYCLE_DURATION = Duration.ofSeconds(2);

    static void main(String[] args) {
        new LambdaHandler(
                new EventNotifyBotJobHandler(),
                EXECUTION_BUFFER,
                CYCLE_PAUSE,
                CYCLE_DURATION
        ).run(
                new TimeLimitingContext(System.currentTimeMillis() + GLOBAL_TIMEOUT.toMillis())
        );
    }

    private record TimeLimitingContext(
            long endTimeMillis
    ) implements Context {

        @Override
        public String getAwsRequestId() {
            return null;
        }

        @Override
        public String getLogGroupName() {
            return null;
        }

        @Override
        public String getLogStreamName() {
            return null;
        }

        @Override
        public String getFunctionName() {
            return null;
        }

        @Override
        public String getFunctionVersion() {
            return null;
        }

        @Override
        public String getInvokedFunctionArn() {
            return null;
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return (int) (endTimeMillis() - System.currentTimeMillis());
        }

        @Override
        public int getMemoryLimitInMB() {
            return 0;
        }

        @Override
        public LambdaLogger getLogger() {
            return null;
        }

    }

}
