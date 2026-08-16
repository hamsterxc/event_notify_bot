package com.lonebytesoft.hamster.eventnotifybot.model.storage;

import java.util.List;

public record Command(
        String id,
        Long chatId,
        Long time,
        String command,
        List<String> parameters
) {
}
