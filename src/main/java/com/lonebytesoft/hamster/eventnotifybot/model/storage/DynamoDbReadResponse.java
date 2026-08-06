package com.lonebytesoft.hamster.eventnotifybot.model.storage;

import java.util.Collection;

public record DynamoDbReadResponse(
        Collection<DynamoDbRecord> records,
        int consumedCapacity
) {
}
