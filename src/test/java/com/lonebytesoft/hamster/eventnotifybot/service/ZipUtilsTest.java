package com.lonebytesoft.hamster.eventnotifybot.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZipUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(ZipUtilsTest.class);

    @Test
    public void test_compressDecompress() throws IOException {
        final Object testData = getTestData();
        final JsonMapper jsonMapper = new JsonMapper();

        final byte[] original = jsonMapper.writeValueAsBytes(testData);
        log.debug("Original: {}", original.length);

        final byte[] compressed = ZipUtils.compress(original);
        log.debug("Compressed: {}", compressed.length);

        final byte[] decompressed = ZipUtils.decompress(compressed);
        log.debug("Decompressed: {}", decompressed.length);

        final Object decompressedData = jsonMapper.readValue(decompressed, Object.class);
        assertEquals(testData, decompressedData);
    }

    private static Object getTestData() {
        final Map<Integer, String> numbers = Map.of(
                1, "one",
                2, "two",
                3, "three",
                4, "four",
                5, "five"
        );
        // something with different data types and repetitions
        return Map.of(
                "numbers", IntStream.range(1, 100)
                        .mapToObj(i -> {
                            final int count = (i - 1) % 5 + 1;
                            return Map.of(
                                    "index", i,
                                    "words", IntStream.rangeClosed(1, count)
                                            .mapToObj(_ -> numbers.get(count))
                                            .collect(Collectors.joining(", ")),
                                    "numbers", IntStream.rangeClosed(1, count)
                                            .mapToObj(_ -> i)
                                            .toList()
                            );
                        })
                        .toList()
        );
    }
  
}
