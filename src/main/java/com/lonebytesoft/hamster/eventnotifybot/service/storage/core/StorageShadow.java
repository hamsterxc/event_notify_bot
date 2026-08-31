package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.RecordId;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class StorageShadow<T extends RecordId> {

    private final Map<String, T> storage;
    private final Map<String, T> local;
    private final Function<T, DynamoDbRecord> recordBuilder;

    protected StorageShadow(
            final Collection<DynamoDbRecord> records,
            final Function<DynamoDbRecord, T> entityBuilder,
            final Function<T, DynamoDbRecord> recordBuilder
    ) {
        this.storage = records
                .stream()
                .map(entityBuilder)
                .collect(Collectors.toMap(RecordId::id, Function.identity()));
        this.local = new HashMap<>(storage);
        this.recordBuilder = recordBuilder;
    }

    public Collection<T> getAll() {
        return local.values();
    }

    public void put(final T value) {
        local.put(value.id(), value);
    }

    public boolean remove(final String id) {
        return local.remove(id) != null;
    }

    public Collection<DynamoDbWriteRequest> flush() {
        final Collection<DynamoDbWriteRequest> writeRequests = Stream.of(storage.keySet(), local.keySet())
                .flatMap(Collection::stream)
                .distinct()
                .map(id -> {
                    final T storageValue = storage.get(id);
                    final T localValue = local.get(id);
                    if (Objects.equals(storageValue, localValue)) {
                        return null;
                    } else if (localValue == null) {
                        return DynamoDbWriteRequest.delete(storageValue.id());
                    } else {
                        return DynamoDbWriteRequest.put(recordBuilder.apply(localValue));
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        storage.clear();
        storage.putAll(local);
        return writeRequests;
    }

}
