package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.function.Function;

class UnknownShadow extends StorageShadow<DynamoDbRecord> {

    private static final Logger log = LoggerFactory.getLogger(SettingsShadow.class);

    public UnknownShadow(
            final Collection<DynamoDbRecord> records
    ) {
        super(records, Function.identity(), Function.identity());
    }

    public int cleanup(final int limit) {
        final Collection<String> cleanupIds = getAll()
                .stream()
                .limit(limit)
                .map(DynamoDbRecord::id)
                .toList();
        if (!cleanupIds.isEmpty()) {
            log.info("Cleaning up {} records of unknown type", cleanupIds.size());
            cleanupIds.forEach(this::remove);
        }
        return cleanupIds.size();
    }

    @Override
    public Collection<DynamoDbWriteRequest> flush() {
        final Collection<DynamoDbWriteRequest> writeRequests = super.flush();
        writeRequests.forEach(writeRequest ->
                log.debug("Removing a record of unknown type: {}", writeRequest));
        return writeRequests;
    }

}
