package com.lonebytesoft.hamster.eventnotifybot.model.storage.record;

public record ProviderStateRecord(
        String id,
        String provider,
        Long time,
        String data
) implements RecordId {
}
