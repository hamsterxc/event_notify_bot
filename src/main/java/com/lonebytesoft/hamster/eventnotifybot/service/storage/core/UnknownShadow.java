package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.UnknownRecord;

import java.util.Collection;
import java.util.function.Function;

class UnknownShadow extends StorageShadow<UnknownRecord> {

    public UnknownShadow(
            final Collection<DynamoDbRecord> records
    ) {
        super(records, entityBuilder(), recordBuilder());
    }

    private static Function<DynamoDbRecord, UnknownRecord> entityBuilder() {
        return record -> new UnknownRecord(
                record.id(),
                record
        );
    }

    private static Function<UnknownRecord, DynamoDbRecord> recordBuilder() {
        return UnknownRecord::record;
    }

}
