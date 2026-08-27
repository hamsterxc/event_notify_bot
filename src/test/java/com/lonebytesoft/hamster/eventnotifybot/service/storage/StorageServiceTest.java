package com.lonebytesoft.hamster.eventnotifybot.service.storage;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.CommandProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SettingsProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StorageServiceTest {

    private final DynamoDbService dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper);

    @Test
    public void settings() {
        Settings settings = storageService.getSettings();
        assertNull(settings.telegramUpdatesOffset()); // nothing was fetched yet

        assertEquals(0, storageService.fetch()); // nothing in storage
        settings = storageService.getSettings(); // no settings were fetched, transparently returning default
        assertNull(settings.telegramUpdatesOffset());
        assertEquals(0, storageService.flush()); // nothing was set, nothing is saved

        settings = new Settings(1L);
        storageService.setSettings(settings);
        assertEquals(1, storageService.flush()); // settings saved
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
    public void settings_multiple() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1001", "settings", null, 101L, serialize(new SettingsProperties(1L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("1002", "settings", null, 102L, serialize(new SettingsProperties(2L))))
        ));

        assertEquals(2, storageService.fetch());
        Settings settings = storageService.getSettings();
        assertEquals(2L, settings.telegramUpdatesOffset()); // record with the greater time is used

        storageService.setSettings(new Settings(3L));
        assertEquals(1, storageService.flush()); // settings saved
        settings = storageService.getSettings();
        assertEquals(3L, settings.telegramUpdatesOffset());

        assertEquals(1, storageService.cleanup(2));
        assertEquals(1, storageService.flush()); // one settings record deleted
        assertEquals(1, storageService.fetch());
        settings = storageService.getSettings();
        assertEquals(3L, settings.telegramUpdatesOffset());
    }

    @Test
    public void commands() {
        final Command first = new Command(null, 1L, 1L, "one", List.of("a", "b", "c"));
        final Command second = new Command(null, 2L, 2L, "two", List.of("first", "second"));

        Collection<Command> commands = storageService.getCommands();
        assertTrue(commands.isEmpty()); // nothing was fetched yet

        assertEquals(0, storageService.fetch()); // nothing in storage
        commands = storageService.getCommands();
        assertTrue(commands.isEmpty()); // no commands in the storage
        assertEquals(0, storageService.flush());

        storageService.putCommand(first);
        assertEquals(1, storageService.flush()); // command added
        commands = storageService.getCommands();
        assertEquals(1, commands.size());
        assertCommandEquals(first, commands.iterator().next());

        storageService.putCommand(second);
        final List<Command> commandsSorted = storageService.getCommands()
                .stream()
                .sorted(Comparator.comparing(Command::command))
                .toList();
        assertEquals(2, commandsSorted.size());
        assertCommandEquals(first, commandsSorted.get(0));
        assertCommandEquals(second, commandsSorted.get(1));

        storageService.removeCommand(commandsSorted.get(1).id());
        assertEquals(0, storageService.flush()); // no command added
        assertEquals(1, storageService.fetch());
        commands = storageService.getCommands();
        assertEquals(1, commands.size());
        assertCommandEquals(first, commands.iterator().next());
    }

    @Test
    public void providerStates() {
        Collection<ProviderState> providerStates = storageService.getProviderStates();
        assertTrue(providerStates.isEmpty()); // nothing was fetched yet

        assertEquals(0, storageService.fetch()); // nothing in storage
        providerStates = storageService.getProviderStates();
        assertTrue(providerStates.isEmpty()); // no provider states in the storage
        assertEquals(0, storageService.flush());

        storageService.setProviderState(new ProviderState("first", 1L, "first-data"));
        assertEquals(1, storageService.flush()); // provider state added
        providerStates = storageService.getProviderStates();
        assertEquals(1, providerStates.size());
        assertProviderStateEquals(new ProviderState("first", 1L, "first-data"), providerStates.iterator().next());

        storageService.setProviderState(new ProviderState("second", 2L, "second-data"));
        final List<ProviderState> providerStatesSorted = storageService.getProviderStates()
                .stream()
                .sorted(Comparator.comparing(ProviderState::provider))
                .toList();
        assertEquals(2, providerStatesSorted.size());
        assertProviderStateEquals(new ProviderState("first", 1L, "first-data"), providerStatesSorted.get(0));
        assertProviderStateEquals(new ProviderState("second", 2L, "second-data"), providerStatesSorted.get(1));

        assertEquals(1, storageService.flush()); // provider state added
        storageService.setProviderState(new ProviderState("second", 2L, "second-data"));
        assertEquals(0, storageService.flush()); // nothing has changed
        storageService.setProviderState(new ProviderState("second", 22L, "second-data"));
        assertEquals(1, storageService.flush()); // time was changed
    }

    @Test
    public void unknown() {
        final Command command = new Command(null, 10002L, 102L, "test", List.of());

        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1001", "settings", null, 101L, serialize(new SettingsProperties(1L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("1002", "command", "10002", 102L, serialize(new CommandProperties("test", List.of())))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("1003", "invalid", null, 103L, null))
        ));

        assertEquals(3, storageService.fetch());
        assertEquals(1L, storageService.getSettings().telegramUpdatesOffset());
        assertEquals(1, storageService.getCommands().size());
        assertCommandEquals(command, storageService.getCommands().iterator().next());

        assertEquals(1, storageService.cleanup(2));
        assertEquals(1, storageService.flush()); // the unknown record removed
        assertEquals(2, storageService.fetch());
        assertEquals(1L, storageService.getSettings().telegramUpdatesOffset());
        assertEquals(1, storageService.getCommands().size());
        assertCommandEquals(command, storageService.getCommands().iterator().next());
    }

    @Test
    public void cleanup() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1001", "settings", null, 101L, serialize(new SettingsProperties(1L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("1002", "settings", null, 102L, serialize(new SettingsProperties(2L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("1003", "command", "10003", 103L, serialize(new CommandProperties("test", List.of())))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("1004", "invalid", null, 104L, null)),
                DynamoDbWriteRequest.put(new DynamoDbRecord("1005", "invalid", null, 105L, null))
        ));

        assertEquals(5, storageService.fetch());
        assertEquals(2, storageService.cleanup(2));
        assertEquals(2, storageService.flush());

        final DynamoDbReadResponse readResponse = dynamoDbService.read();
        assertEquals(3, readResponse.consumedCapacity());

        Map<String, List<DynamoDbRecord>> records = readResponse.records()
                .stream()
                .collect(Collectors.groupingBy(DynamoDbRecord::type));
        assertEquals(1, records.get("command").size());
        assertFalse(records.get("settings").isEmpty());
        assertEquals(2, records.get("settings").size() + records.get("invalid").size()); // it is undefined, which records exactly are cleaned up

        assertEquals(1, storageService.cleanup(2));
        assertEquals(1, storageService.flush());
        records = readResponse.records()
                .stream()
                .collect(Collectors.groupingBy(DynamoDbRecord::type));
        assertEquals(2, records.size()); // now all extra records are removed
        assertEquals(1, records.get("command").size());
        assertEquals(1, records.get("settings").size());
    }

    private byte[] serialize(Object value) {
        return ZipUtils.compress(jsonMapper.writeValueAsBytes(value));
    }

    private static void assertCommandEquals(final Command expected, final Command actual) {
        assertEquals(expected.chatId(), actual.chatId());
        assertEquals(expected.time(), actual.time());
        assertEquals(expected.command(), actual.command());
        assertEquals(expected.parameters(), actual.parameters());
    }
    
    private static void assertProviderStateEquals(final ProviderState expected, final ProviderState actual) {
        assertEquals(expected.provider(), actual.provider());
        assertEquals(expected.time(), actual.time());
        assertEquals(expected.data(), actual.data());
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
                    storage.size()
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
