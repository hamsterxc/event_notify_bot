package com.lonebytesoft.hamster.eventnotifybot.model.storage.record;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.CommandProperties;

public record CommandRecord(
        String id,
        Long chatId,
        Long time,
        CommandProperties properties
) implements RecordId {
}
