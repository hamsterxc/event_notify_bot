package com.lonebytesoft.hamster.eventnotifybot.model.storage.record;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;

public record SubscriptionRecord(
        String id,
        Long chatId,
        Long time,
        SubscriptionProperties properties
) implements RecordId {
}
