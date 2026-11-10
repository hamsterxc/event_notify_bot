package com.lonebytesoft.hamster.eventnotifybot.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "abcdefghij,12,abcdefghij",
            "abcdefghij,10,abcdefghij",
            "abcdefghij,9,abcdef...",
            "abcdefghij,5,ab...",
            "abcdefghij,3,...",
            "abcdefghij,2,''",
            "abcdefghij,0,''"
    })
    public void truncate(final String s, final int limit, final String expected) {
        assertEquals(expected, StringUtils.truncate(s, limit));
    }

}
