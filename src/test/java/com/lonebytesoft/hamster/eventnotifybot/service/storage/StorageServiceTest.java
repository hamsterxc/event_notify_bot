package com.lonebytesoft.hamster.eventnotifybot.service.storage;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.Settings;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StorageServiceTest {

    private final StorageService storageService = new StorageService(
            new DynamoDbServiceMock(),
            new JsonMapper()
    );

    @Test
    public void settings() {
        Settings settings = storageService.getSettings();
        assertNull(settings); // nothing was fetched yet

        assertEquals(1, storageService.fetch());
        settings = storageService.getSettings(); // no settings were fetched, transparently returning default
        assertNull(settings.telegramUpdatesOffset());
        assertEquals(1, storageService.flush()); // default settings saved

        settings = new Settings(1L);
        storageService.setSettings(settings);
        assertEquals(1, storageService.flush()); // settings updated
        settings = storageService.getSettings();
        assertEquals(1L, settings.telegramUpdatesOffset());

        settings = new Settings(2L);
        storageService.setSettings(settings);
        settings = new Settings(1L);
        storageService.setSettings(settings);
        assertEquals(0, storageService.flush()); // settings stay the same, not updated
        settings = storageService.getSettings();
        assertEquals(1L, settings.telegramUpdatesOffset());
    }

    private static class DynamoDbServiceMock extends DynamoDbService {

        private final Map<String, DynamoDbRecord> storage = new HashMap<>();

        public DynamoDbServiceMock() {
            super(null, null);
        }

        @Override
        public DynamoDbReadResponse read() {
            return new DynamoDbReadResponse(
                    storage.values(),
                    1
            );
        }

        @Override
        public int write(Collection<DynamoDbWriteRequest> writeRequests) {
            writeRequests
                    .stream()
                    .map(DynamoDbWriteRequest::toDynamoDbRequest)
                    .forEach(writeRequest -> Optional.ofNullable(writeRequest.putRequest())
                            .map(PutRequest::item)
                            .map(DynamoDbRecord::new)
                            .ifPresentOrElse(
                                    record -> storage.put(record.id(), record),
                                    () -> Optional.ofNullable(writeRequest.deleteRequest())
                                            .map(DeleteRequest::key)
                                            .map(key -> key.get("id"))
                                            .map(AttributeValue::s)
                                            .ifPresentOrElse(
                                                    storage::remove,
                                                    () -> {
                                                        throw new IllegalArgumentException("Unprocessable write request: " + writeRequest);
                                                    }
                                            )
                            ));
            return writeRequests.size();
        }

    }

}
