package com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb;

import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
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

    @Override
    public String toString() {
        return "DynamoDbRecord[id=%s, type=%s, subject=%s, time=%d, data=%s]".formatted(
                id,
                type,
                subject,
                time,
                Optional.ofNullable(data)
                        .map(ZipUtils::decompress)
                        .map(String::new)
                        .orElse(null)
        );
    }

}
