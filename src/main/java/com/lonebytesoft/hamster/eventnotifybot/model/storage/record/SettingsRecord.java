package com.lonebytesoft.hamster.eventnotifybot.model.storage.record;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;

public record SettingsRecord(
        String id,
        Long time,
        Settings settings
) {
}
