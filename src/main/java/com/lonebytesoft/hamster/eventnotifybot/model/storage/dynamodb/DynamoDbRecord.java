package com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public record DynamoDbRecord(
        String id,
        String type,
        String subject,
        Long time,
        byte[] data
) {

    public DynamoDbRecord(final Map<String, AttributeValue> dynamoDbAttributes) {
        this(
                safeExtract(dynamoDbAttributes.get("id"), attribute -> attribute.map(AttributeValue::s)),
                safeExtract(dynamoDbAttributes.get("type"), attribute -> attribute.map(AttributeValue::s)),
                safeExtract(dynamoDbAttributes.get("subject"), attribute -> attribute.map(AttributeValue::s)),
                safeExtract(dynamoDbAttributes.get("time"), attribute -> attribute.map(AttributeValue::n).map(Long::valueOf)),
                safeExtract(dynamoDbAttributes.get("data"), attribute -> attribute.map(AttributeValue::b).map(SdkBytes::asByteArray))
        );
    }

    private static <T> T safeExtract(
            final AttributeValue attribute,
            final Function<Optional<AttributeValue>, Optional<T>> converter
    ) {
        return converter.apply(Optional.ofNullable(attribute))
                .orElse(null);
    }

}
