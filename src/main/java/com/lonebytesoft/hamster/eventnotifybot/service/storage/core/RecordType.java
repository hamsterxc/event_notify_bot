package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

enum RecordType {

    SETTINGS,
    COMMAND,
    UNKNOWN
    ;

    private static final Map<String, RecordType> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toMap(RecordType::getValue, Function.identity()));

    public static RecordType fromValue(final String value) {
        return Optional.ofNullable(value)
                .map(String::toLowerCase)
                .map(BY_VALUE::get)
                .orElse(RecordType.UNKNOWN);
    }

    public String getValue() {
        return this.name().toLowerCase();
    }
    
}
