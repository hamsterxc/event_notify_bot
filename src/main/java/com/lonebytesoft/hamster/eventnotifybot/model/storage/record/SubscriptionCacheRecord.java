package com.lonebytesoft.hamster.eventnotifybot.model.storage.record;

public record SubscriptionCacheRecord(
        String id,
        String provider,
        Long time,
        String data
) implements RecordId {
}
