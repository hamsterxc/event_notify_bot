package com.lonebytesoft.hamster.eventnotifybot.test;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class Assertions {

    private Assertions() {
        throw new UnsupportedOperationException();
    }

    public static void assertDynamoDbRecordsEquals(
            final Collection<DynamoDbRecord> expected,
            final Collection<DynamoDbRecord> actual
    ) {
        final Supplier<String> failureMessage = () -> "actual records: " + actual;
        if (expected == null) {
            assertNull(actual, failureMessage);
            return;
        } else {
            assertNotNull(actual);
        }

        assertEquals(expected.size(), actual.size(), failureMessage);

        final Collection<DynamoDbRecord> actualRecords = new HashSet<>(actual);
        expected.forEach(expectedRecord -> {
            for (final Iterator<DynamoDbRecord> iterator = actualRecords.iterator(); iterator.hasNext(); ) {
                if (isDynamoDbRecordEqual(expectedRecord, iterator.next())) {
                    iterator.remove();
                    break;
                }
            }
        });
        assertTrue(actualRecords.isEmpty(), failureMessage);
    }

    private static boolean isDynamoDbRecordEqual(
            final DynamoDbRecord expected,
            final DynamoDbRecord actual
    ) {
        return ((expected.id() == null) || Objects.equals(expected.id(), actual.id()))
                && Objects.equals(expected.type(), actual.type())
                && Objects.equals(expected.subject(), actual.subject())
                && ((expected.time() == null) || Objects.equals(expected.time(), actual.time()))
                && Objects.equals(new String(ZipUtils.decompress(expected.data())), new String(ZipUtils.decompress(actual.data())));
    }

}
