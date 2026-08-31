package com.lonebytesoft.hamster.eventnotifybot.model.storage.record;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;

public record UnknownRecord(
        String id,
        DynamoDbRecord record
) implements RecordId {
}
