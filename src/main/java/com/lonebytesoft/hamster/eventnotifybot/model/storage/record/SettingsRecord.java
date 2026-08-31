package com.lonebytesoft.hamster.eventnotifybot.model.storage.record;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SettingsProperties;

public record SettingsRecord(
        String id,
        Long time,
        SettingsProperties properties
) implements RecordId {
}
