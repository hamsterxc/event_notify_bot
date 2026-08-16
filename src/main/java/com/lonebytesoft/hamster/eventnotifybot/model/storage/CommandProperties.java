package com.lonebytesoft.hamster.eventnotifybot.model.storage;

import java.util.List;

public record CommandProperties(
        String command,
        List<String> parameters
) {
}
