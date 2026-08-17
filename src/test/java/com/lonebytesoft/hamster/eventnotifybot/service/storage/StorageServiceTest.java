package com.lonebytesoft.hamster.eventnotifybot.service.storage;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StorageServiceTest {

    private final StorageService storageService = new StorageService(
            new DynamoDbServiceMock(),
            new JsonMapper()
    );

    @Test
    public void settings() {
        Settings settings = storageService.getSettings();
        assertNull(settings.telegramUpdatesOffset()); // nothing was fetched yet

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

    @Test
    public void commands() {
        Collection<Command> commands = storageService.getCommands();
        assertTrue(commands.isEmpty()); // nothing was fetched yet

        assertEquals(1, storageService.fetch());
        storageService.flush(); // flushing settings
        commands = storageService.getCommands();
        assertTrue(commands.isEmpty()); // no commands in the storage
        assertEquals(0, storageService.flush());

        storageService.addCommand(1L, 1L, "one", List.of("a", "b", "c"));
        assertEquals(1, storageService.flush()); // command added
        commands = storageService.getCommands();
        assertEquals(1, commands.size());
        assertCommandEquals(new Command(null, 1L, 1L, "one", List.of("a", "b", "c")), commands.iterator().next());

        storageService.addCommand(2L, 2L, "two", List.of("first", "second"));
        final List<Command> commandsSorted = storageService.getCommands()
                .stream()
                .sorted(Comparator.comparing(Command::command))
                .toList();
        assertEquals(2, commandsSorted.size());
        assertCommandEquals(new Command(null, 1L, 1L, "one", List.of("a", "b", "c")), commandsSorted.get(0));
        assertCommandEquals(new Command(null, 2L, 2L, "two", List.of("first", "second")), commandsSorted.get(1));

        storageService.removeCommand(commandsSorted.get(1).id());
        assertEquals(0, storageService.flush()); // no command added
        assertEquals(1, storageService.fetch());
        commands = storageService.getCommands();
        assertEquals(1, commands.size());
        assertCommandEquals(new Command(null, 1L, 1L, "one", List.of("a", "b", "c")), commands.iterator().next());
    }

    private static void assertCommandEquals(final Command expected, final Command actual) {
        assertEquals(expected.chatId(), actual.chatId());
        assertEquals(expected.time(), actual.time());
        assertEquals(expected.command(), actual.command());
        assertEquals(expected.parameters(), actual.parameters());
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
