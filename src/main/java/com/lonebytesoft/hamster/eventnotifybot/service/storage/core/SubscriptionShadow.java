package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.SubscriptionRecord;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.function.Function;

class SubscriptionShadow extends StorageShadow<SubscriptionRecord> {

    public SubscriptionShadow(
            final Collection<DynamoDbRecord> records,
            final JsonMapper jsonMapper
    ) {
        super(records, entityBuilder(jsonMapper), recordBuilder(jsonMapper));
    }

    private static Function<DynamoDbRecord, SubscriptionRecord> entityBuilder(final JsonMapper jsonMapper) {
        return record -> new SubscriptionRecord(
                record.id(),
                Long.valueOf(record.subject()),
                record.time(),
                jsonMapper.readValue(ZipUtils.decompress(record.data()), SubscriptionProperties.class)
        );
    }

    private static Function<SubscriptionRecord, DynamoDbRecord> recordBuilder(final JsonMapper jsonMapper) {
        return subscriptionRecord -> new DynamoDbRecord(
                subscriptionRecord.id(),
                RecordType.SUBSCRIPTION.getValue(),
                String.valueOf(subscriptionRecord.chatId()),
                subscriptionRecord.time(),
                ZipUtils.compress(jsonMapper.writeValueAsBytes(subscriptionRecord.properties()))
        );
    }

}
