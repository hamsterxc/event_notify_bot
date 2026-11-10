package com.lonebytesoft.hamster.eventnotifybot.service;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StringUtils {

    private static final String ELLIPSIS = "...";

    private StringUtils() {
        throw new UnsupportedOperationException();
    }

    public static String truncate(final String s, final int limit) {
        if (s.length() <= limit) {
            return s;
        } else {
            return limit < ELLIPSIS.length()
                    ? ""
                    : s.substring(0, limit - ELLIPSIS.length()).trim() + ELLIPSIS;
        }
    }

    public static String extractMessageChain(final Throwable e) {
        return Stream.iterate(e, Objects::nonNull, Throwable::getCause)
                .map(Throwable::getMessage)
                .collect(Collectors.joining(" -> "));
    }

}
