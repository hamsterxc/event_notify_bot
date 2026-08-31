package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.ProviderStateRecord;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;

import java.util.Collection;
import java.util.function.Function;

class ProviderStateShadow extends StorageShadow<ProviderStateRecord> {

    public ProviderStateShadow(
            final Collection<DynamoDbRecord> records
    ) {
        super(records, entityBuilder(), recordBuilder());
    }

    private static Function<DynamoDbRecord, ProviderStateRecord> entityBuilder() {
        return record -> new ProviderStateRecord(
                record.id(),
                record.subject(),
                record.time(),
                new String(ZipUtils.decompress(record.data()))
        );
    }

    private static Function<ProviderStateRecord, DynamoDbRecord> recordBuilder() {
        return entity -> new DynamoDbRecord(
                entity.id(),
                RecordType.PROVIDER_STATE.getValue(),
                entity.provider(),
                entity.time(),
                ZipUtils.compress(entity.data().getBytes())
        );
    }

}
