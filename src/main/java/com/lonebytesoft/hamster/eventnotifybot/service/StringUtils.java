package com.lonebytesoft.hamster.eventnotifybot.service;

public class StringUtils {

    private static final String ELLIPSIS = "...";

    public static String truncate(final String s, final int limit) {
        if (s.length() <= limit) {
            return s;
        } else {
            return limit < ELLIPSIS.length()
                    ? ""
                    : s.substring(0, limit - ELLIPSIS.length()).trim() + ELLIPSIS;
        }
    }

}
