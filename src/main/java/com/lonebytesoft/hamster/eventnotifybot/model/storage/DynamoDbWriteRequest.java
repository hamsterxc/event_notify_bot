package com.lonebytesoft.hamster.eventnotifybot.model.storage;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.Map;

import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.createB;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.createN;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.createS;

public sealed interface DynamoDbWriteRequest {

    WriteRequest toDynamoDbRequest();

    static DynamoDbWriteRequest put(final DynamoDbRecord record) {
        return new Put(record);
    }

    record Put(
            DynamoDbRecord record
    ) implements DynamoDbWriteRequest {
        @Override
        public WriteRequest toDynamoDbRequest() {
            return WriteRequest.builder()
                    .putRequest(putRequest -> putRequest
                            .item(Map.of(
                                    "id", createS(record.id()),
                                    "type", createS(record.type()),
                                    "subject", createS(record.subject()),
                                    "time", createN(String.valueOf(record.time())),
                                    "data", createB(SdkBytes.fromByteArray(record.data()))
                            )))
                    .build();
        }
    }

    static DynamoDbWriteRequest delete(final String id) {
        return new Delete(id);
    }

    record Delete(
            String id
    ) implements DynamoDbWriteRequest {
        @Override
        public WriteRequest toDynamoDbRequest() {
            return WriteRequest.builder()
                    .deleteRequest(deleteRequest -> deleteRequest
                            .key(Map.of(
                                    "id", createS(id)
                            )))
                    .build();
        }
    }

}
