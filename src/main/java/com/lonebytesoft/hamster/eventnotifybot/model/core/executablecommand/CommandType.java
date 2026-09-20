package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CommandType {

    TEST, // todo: remove when commands suitable for existing tests are implemented
    START,
    HELP,
    INVALID,
    UNKNOWN,
    ;

    private static final Map<String, CommandType> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toMap(CommandType::getValue, Function.identity()));

    public static CommandType fromValue(final String value) {
        return Optional.ofNullable(value)
                .map(String::toLowerCase)
                .map(BY_VALUE::get)
                .orElse(UNKNOWN);
    }

    public String getValue() {
        return this.name().toLowerCase();
    }

}
