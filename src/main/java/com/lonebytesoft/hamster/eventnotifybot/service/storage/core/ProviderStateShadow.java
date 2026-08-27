package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.ProviderStateRecord;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

class ProviderStateShadow extends StorageShadow<ProviderStateRecord> {

    public ProviderStateShadow(
            final Collection<DynamoDbRecord> records
    ) {
        super(records, entityBuilder(), recordBuilder());
    }

    private static Function<DynamoDbRecord, ProviderStateRecord> entityBuilder() {
        return record -> {
            final ProviderState providerState = new ProviderState(
                    record.subject(),
                    record.time(),
                    new String(ZipUtils.decompress(record.data()))
            );
            return new ProviderStateRecord(
                    record.id(),
                    providerState
            );
        };
    }

    private static Function<ProviderStateRecord, DynamoDbRecord> recordBuilder() {
        return providerStateRecord -> {
            final Optional<ProviderState> providerState = Optional.ofNullable(providerStateRecord.providerState());
            return new DynamoDbRecord(
                    providerStateRecord.id(),
                    RecordType.PROVIDER_STATE.getValue(),
                    providerState.map(ProviderState::provider).orElse(null),
                    providerState.map(ProviderState::time).orElse(null),
                    providerState.map(ProviderState::data).map(String::getBytes).map(ZipUtils::compress).orElse(null)
            );
        };
    }

    public Collection<ProviderState> getAll() {
        return getLocal()
                .stream()
                .map(ProviderStateRecord::providerState)
                .filter(Objects::nonNull)
                .toList();
    }

    public void set(final ProviderState providerState) {
        final String id = getLocal()
                .stream()
                .filter(record -> record.providerState() != null)
                .filter(record -> Objects.equals(record.providerState().provider(), providerState.provider()))
                .findFirst()
                .map(ProviderStateRecord::id)
                .orElseGet(() -> UUID.randomUUID().toString());
        put(id, new ProviderStateRecord(id, providerState));
    }

}
