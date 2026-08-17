package com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.createNul;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.createS;

public sealed interface DynamoDbWriteRequest {

    WriteRequest toDynamoDbRequest();

    static DynamoDbWriteRequest put(final DynamoDbRecord record) {
        return new $DynamoDbWriteRequest.Put(record);
    }

    static DynamoDbWriteRequest delete(final String id) {
        return new $DynamoDbWriteRequest.Delete(id);
    }

    final class $DynamoDbWriteRequest {

        private static <T> AttributeValue safeWrap(
                final T value,
                final Function<Optional<T>, Optional<AttributeValue>> converter
        ) {
            return converter.apply(Optional.ofNullable(value))
                    .orElse(createNul(true));
        }

        private record Put(
                DynamoDbRecord record
        ) implements DynamoDbWriteRequest {
            @Override
            public WriteRequest toDynamoDbRequest() {
                if (record.id() == null) {
                    throw new IllegalArgumentException("id cannot be null for DynamoDB put request");
                }
                return WriteRequest.builder()
                        .putRequest(putRequest -> putRequest
                                .item(Map.of(
                                        "id", createS(record.id()),
                                        "type", safeWrap(record.type(), value -> value.map(AttributeValue::createS)),
                                        "subject", safeWrap(record.subject(), value -> value.map(AttributeValue::createS)),
                                        "time", safeWrap(record.time(), value -> value.map(String::valueOf).map(AttributeValue::createN)),
                                        "data", safeWrap(record.data(), value -> value.map(SdkBytes::fromByteArray).map(AttributeValue::createB))
                                )))
                        .build();
            }
        }

        private record Delete(
                String id
        ) implements DynamoDbWriteRequest {
            @Override
            public WriteRequest toDynamoDbRequest() {
                if (id == null) {
                    throw new IllegalArgumentException("id cannot be null for DynamoDB delete request");
                }
                return WriteRequest.builder()
                        .deleteRequest(deleteRequest -> deleteRequest
                                .key(Map.of(
                                        "id", createS(id)
                                )))
                        .build();
            }
        }

    }

}
