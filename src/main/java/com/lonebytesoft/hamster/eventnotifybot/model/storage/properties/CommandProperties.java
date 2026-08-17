package com.lonebytesoft.hamster.eventnotifybot.model.storage.properties;

import java.util.List;

public record CommandProperties(
        String command,
        List<String> parameters
) {
}
