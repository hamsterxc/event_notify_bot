package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.SubscriptionCacheRecord;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;

import java.util.Collection;
import java.util.function.Function;

class SubscriptionCacheShadow extends StorageShadow<SubscriptionCacheRecord> {

    public SubscriptionCacheShadow(
            final Collection<DynamoDbRecord> records
    ) {
        super(records, entityBuilder(), recordBuilder());
    }

    private static Function<DynamoDbRecord, SubscriptionCacheRecord> entityBuilder() {
        return record -> new SubscriptionCacheRecord(
                record.id(),
                record.subject(),
                record.time(),
                new String(ZipUtils.decompress(record.data()))
        );
    }

    private static Function<SubscriptionCacheRecord, DynamoDbRecord> recordBuilder() {
        return subscriptionCacheRecord -> new DynamoDbRecord(
                subscriptionCacheRecord.id(),
                RecordType.SUBSCRIPTION_CACHE.getValue(),
                subscriptionCacheRecord.provider(),
                subscriptionCacheRecord.time(),
                ZipUtils.compress(subscriptionCacheRecord.data().getBytes())
        );
    }

}
