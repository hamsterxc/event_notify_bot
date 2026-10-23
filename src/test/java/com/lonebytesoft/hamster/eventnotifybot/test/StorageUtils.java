package com.lonebytesoft.hamster.eventnotifybot.test;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class StorageUtils {

    private final DynamoDbService dynamoDbService;
    private final JsonMapper jsonMapper;

    public StorageUtils(
            final DynamoDbService dynamoDbService,
            final JsonMapper jsonMapper
    ) {
        this.dynamoDbService = dynamoDbService;
        this.jsonMapper = jsonMapper;
    }

    public Map<String, String> getSubscriptionCacheRefs() {
        return dynamoDbService.read().records()
                .stream()
                .filter(record -> "subscription".equals(record.type()))
                .collect(Collectors.toMap(
                        DynamoDbRecord::id,
                        record -> Optional.ofNullable(record.data())
                                .map(ZipUtils::decompress)
                                .map(data -> jsonMapper.readValue(data, SubscriptionProperties.class))
                                .map(SubscriptionProperties::cacheRef)
                                .orElseThrow(() -> new IllegalStateException("No cache ref for subscription: " + record))
                ));
    }

}
