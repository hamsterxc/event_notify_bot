package com.lonebytesoft.hamster.eventnotifybot;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.lonebytesoft.hamster.eventnotifybot.handler.LambdaHandler;
import com.lonebytesoft.hamster.eventnotifybot.handler.TestJobHandler;

import java.time.Duration;

public class Test {

    static void main(String[] args) {
        new LambdaHandler(
                new TestJobHandler(),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1)
        ).run(
                new TimeLimitingContext(System.currentTimeMillis() + Duration.ofMinutes(1).toMillis())
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
