package com.lonebytesoft.hamster.eventnotifybot.service.storage;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Subscription;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.CommandProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SettingsProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import com.lonebytesoft.hamster.eventnotifybot.test.DynamoDbServiceMock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StorageServiceTest {

    private final DynamoDbService dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper, 20);

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
        assertEquals(1, storageService.flush()); // settings time was updated
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

        storageService.addCommand(first);
        assertEquals(1, storageService.flush()); // command added
        commands = storageService.getCommands();
        assertEquals(1, commands.size());
        assertCommandEquals(first, commands.iterator().next());

        storageService.addCommand(second);
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
    public void subscriptions() {
        assertEquals(0, storageService.fetch());
        Collection<Subscription> subscriptions = storageService.getSubscriptions();
        assertEquals(0, subscriptions.size());
        assertEquals(0, storageService.flush());

        // one subscription and its one cache record

        assertTrue(storageService.addSubscription(new Subscription(1L, "provider-1", "data-1")));
        subscriptions = storageService.getSubscriptions();
        assertEquals(1, subscriptions.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptions.iterator().next());

        assertEquals(2, storageService.flush()); // subscription and subscription cache records added
        DynamoDbReadResponse readResponse = dynamoDbService.read();
        assertEquals(2, readResponse.consumedCapacity()); // subscription and subscription cache records
        Map<String, List<DynamoDbRecord>> records = readResponse.records()
                .stream()
                .collect(Collectors.groupingBy(DynamoDbRecord::type));
        assertEquals(2, records.size());

        Collection<DynamoDbRecord> subscriptionCacheRecords = records.get("subscription_cache");
        assertEquals(1, subscriptionCacheRecords.size());
        DynamoDbRecord subscriptionCacheRecord = subscriptionCacheRecords.iterator().next();
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionCacheRecord.id(),
                        "subscription_cache",
                        "provider-1",
                        subscriptionCacheRecord.time(),
                        ZipUtils.compress("data-1".getBytes())
                ),
                subscriptionCacheRecord
        );

        Collection<DynamoDbRecord> subscriptionRecords = records.get("subscription");
        assertEquals(1, subscriptionRecords.size());
        DynamoDbRecord subscriptionRecord = subscriptionRecords.iterator().next();
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionRecord.id(),
                        "subscription",
                        "1",
                        subscriptionRecord.time(),
                        serialize(Map.of("cacheRef", subscriptionCacheRecord.id()))
                ),
                subscriptionRecord
        );

        // two subscriptions and their one cache record

        assertTrue(storageService.addSubscription(new Subscription(2L, "provider-1", "data-1")));
        List<Subscription> subscriptionsSorted = storageService.getSubscriptions()
                .stream()
                .sorted(Comparator.comparing(Subscription::chatId))
                .toList();
        assertEquals(2, subscriptionsSorted.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptionsSorted.get(0));
        assertSubscriptionEquals(new Subscription(2L, "provider-1", "data-1"), subscriptionsSorted.get(1));

        assertEquals(1, storageService.flush()); // only the second subscription record added
        readResponse = dynamoDbService.read();
        assertEquals(3, readResponse.consumedCapacity()); // two subscription and one subscription cache records
        records = readResponse.records()
                .stream()
                .collect(Collectors.groupingBy(DynamoDbRecord::type));
        assertEquals(2, records.size());

        subscriptionCacheRecords = records.get("subscription_cache");
        assertEquals(1, subscriptionCacheRecords.size());
        subscriptionCacheRecord = subscriptionCacheRecords.iterator().next();
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionCacheRecord.id(),
                        "subscription_cache",
                        "provider-1",
                        subscriptionCacheRecord.time(),
                        ZipUtils.compress("data-1".getBytes())
                ),
                subscriptionCacheRecord
        );

        List<DynamoDbRecord> subscriptionRecordsSorted = records.get("subscription")
                .stream()
                .sorted(Comparator.comparing(DynamoDbRecord::subject))
                .toList();
        assertEquals(2, subscriptionRecordsSorted.size());
        subscriptionRecord = subscriptionRecordsSorted.get(0);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionRecord.id(),
                        "subscription",
                        "1",
                        subscriptionRecord.time(),
                        serialize(Map.of("cacheRef", subscriptionCacheRecord.id()))
                ),
                subscriptionRecord
        );
        subscriptionRecord = subscriptionRecordsSorted.get(1);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionRecord.id(),
                        "subscription",
                        "2",
                        subscriptionRecord.time(),
                        serialize(Map.of("cacheRef", subscriptionCacheRecord.id()))
                ),
                subscriptionRecord
        );

        // two subscriptions and their two different (by data) cache records

        assertTrue(storageService.updateSubscription(new Subscription(2L, "provider-1", "data-2")));
        subscriptionsSorted = storageService.getSubscriptions()
                .stream()
                .sorted(Comparator.comparing(Subscription::chatId))
                .toList();
        assertEquals(2, subscriptionsSorted.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptionsSorted.get(0));
        assertSubscriptionEquals(new Subscription(2L, "provider-1", "data-2"), subscriptionsSorted.get(1));

        assertEquals(2, storageService.flush()); // second subscription record updated and one subscription cache record added
        readResponse = dynamoDbService.read();
        assertEquals(4, readResponse.consumedCapacity()); // two subscription and two subscription cache records
        records = readResponse.records()
                .stream()
                .collect(Collectors.groupingBy(DynamoDbRecord::type));
        assertEquals(2, records.size());

        List<DynamoDbRecord> subscriptionCacheRecordsSorted = records.get("subscription_cache")
                .stream()
                .sorted(Comparator.comparing(record -> new String(ZipUtils.decompress(record.data()))))
                .toList();
        assertEquals(2, subscriptionCacheRecordsSorted.size());
        subscriptionCacheRecord = subscriptionCacheRecordsSorted.get(0);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionCacheRecord.id(),
                        "subscription_cache",
                        "provider-1",
                        subscriptionCacheRecord.time(),
                        ZipUtils.compress("data-1".getBytes())
                ),
                subscriptionCacheRecord
        );
        subscriptionCacheRecord = subscriptionCacheRecordsSorted.get(1);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionCacheRecord.id(),
                        "subscription_cache",
                        "provider-1",
                        subscriptionCacheRecord.time(),
                        ZipUtils.compress("data-2".getBytes())
                ),
                subscriptionCacheRecord
        );

        subscriptionRecordsSorted = records.get("subscription")
                .stream()
                .sorted(Comparator.comparing(DynamoDbRecord::subject))
                .toList();
        assertEquals(2, subscriptionRecordsSorted.size());
        subscriptionRecord = subscriptionRecordsSorted.get(0);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionRecord.id(),
                        "subscription",
                        "1",
                        subscriptionRecord.time(),
                        serialize(Map.of("cacheRef", subscriptionCacheRecordsSorted.get(0).id()))
                ),
                subscriptionRecord
        );
        subscriptionRecord = subscriptionRecordsSorted.get(1);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionRecord.id(),
                        "subscription",
                        "2",
                        subscriptionRecord.time(),
                        serialize(Map.of("cacheRef", subscriptionCacheRecordsSorted.get(1).id()))
                ),
                subscriptionRecord
        );

        // two subscriptions and their two different (by provider) cache records

        assertTrue(storageService.removeSubscription(2L, "provider-1"));
        assertEquals(1, storageService.flush());

        assertTrue(storageService.addSubscription(new Subscription(2L, "provider-2", "data-1")));
        subscriptionsSorted = storageService.getSubscriptions()
                .stream()
                .sorted(Comparator.comparing(Subscription::chatId))
                .toList();
        assertEquals(2, subscriptionsSorted.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptionsSorted.get(0));
        assertSubscriptionEquals(new Subscription(2L, "provider-2", "data-1"), subscriptionsSorted.get(1));

        assertEquals(2, storageService.flush()); // one subscription record and one subscription cache record added
        readResponse = dynamoDbService.read();
        assertEquals(5, readResponse.consumedCapacity()); // two subscription and three subscription cache records
        records = readResponse.records()
                .stream()
                .collect(Collectors.groupingBy(DynamoDbRecord::type));
        assertEquals(2, records.size());

        subscriptionCacheRecordsSorted = records.get("subscription_cache")
                .stream()
                .sorted(Comparator.comparing(DynamoDbRecord::subject)
                        .thenComparing(record -> new String(ZipUtils.decompress(record.data()))))
                .toList();
        assertEquals(3, subscriptionCacheRecordsSorted.size());
        subscriptionCacheRecord = subscriptionCacheRecordsSorted.get(0);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionCacheRecord.id(),
                        "subscription_cache",
                        "provider-1",
                        subscriptionCacheRecord.time(),
                        ZipUtils.compress("data-1".getBytes())
                ),
                subscriptionCacheRecord
        );
        subscriptionCacheRecord = subscriptionCacheRecordsSorted.get(1);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionCacheRecord.id(),
                        "subscription_cache",
                        "provider-1",
                        subscriptionCacheRecord.time(),
                        ZipUtils.compress("data-2".getBytes())
                ),
                subscriptionCacheRecord
        );
        subscriptionCacheRecord = subscriptionCacheRecordsSorted.get(2);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionCacheRecord.id(),
                        "subscription_cache",
                        "provider-2",
                        subscriptionCacheRecord.time(),
                        ZipUtils.compress("data-1".getBytes())
                ),
                subscriptionCacheRecord
        );

        subscriptionRecordsSorted = records.get("subscription")
                .stream()
                .sorted(Comparator.comparing(DynamoDbRecord::subject))
                .toList();
        assertEquals(2, subscriptionRecordsSorted.size());
        subscriptionRecord = subscriptionRecordsSorted.get(0);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionRecord.id(),
                        "subscription",
                        "1",
                        subscriptionRecord.time(),
                        serialize(Map.of("cacheRef", subscriptionCacheRecordsSorted.get(0).id()))
                ),
                subscriptionRecord
        );
        subscriptionRecord = subscriptionRecordsSorted.get(1);
        assertDynamoDbRecordEquals(
                new DynamoDbRecord(
                        subscriptionRecord.id(),
                        "subscription",
                        "2",
                        subscriptionRecord.time(),
                        serialize(Map.of("cacheRef", subscriptionCacheRecordsSorted.get(2).id()))
                ),
                subscriptionRecord
        );
    }

    @Test
    public void subscriptions_addDuplicate() {
        assertEquals(0, storageService.fetch());
        Collection<Subscription> subscriptions = storageService.getSubscriptions();
        assertEquals(0, subscriptions.size());
        assertEquals(0, storageService.flush());

        assertTrue(storageService.addSubscription(new Subscription(1L, "provider-1", "data-1")));
        assertEquals(2, storageService.flush());
        subscriptions = storageService.getSubscriptions();
        assertEquals(1, subscriptions.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptions.iterator().next());

        // trying to add the same subscription fails
        assertFalse(storageService.addSubscription(new Subscription(1L, "provider-1", "data-1")));
        assertEquals(0, storageService.flush());
        subscriptions = storageService.getSubscriptions();
        assertEquals(1, subscriptions.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptions.iterator().next());
    }

    @Test
    public void subscriptions_updateNonExistent() {
        assertEquals(0, storageService.fetch());
        Collection<Subscription> subscriptions = storageService.getSubscriptions();
        assertEquals(0, subscriptions.size());
        assertEquals(0, storageService.flush());

        // trying to update a non-existent subscription fails
        assertFalse(storageService.updateSubscription(new Subscription(1L, "provider-1", "data-1")));
        assertEquals(0, storageService.flush());
        subscriptions = storageService.getSubscriptions();
        assertEquals(0, subscriptions.size());
    }

    @Test
    public void subscriptions_removeNonExistent() {
        assertEquals(0, storageService.fetch());
        Collection<Subscription> subscriptions = storageService.getSubscriptions();
        assertEquals(0, subscriptions.size());
        assertEquals(0, storageService.flush());

        assertTrue(storageService.addSubscription(new Subscription(1L, "provider-1", "data-1")));
        assertEquals(2, storageService.flush());
        subscriptions = storageService.getSubscriptions();
        assertEquals(1, subscriptions.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptions.iterator().next());

        // trying to remove a non-existent (by provider) subscription fails
        assertFalse(storageService.removeSubscription(1L, "provider-2"));
        assertEquals(0, storageService.flush());
        subscriptions = storageService.getSubscriptions();
        assertEquals(1, subscriptions.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptions.iterator().next());

        // trying to remove a non-existent (by chat) subscription fails
        assertFalse(storageService.removeSubscription(2L, "provider-1"));
        assertEquals(0, storageService.flush());
        subscriptions = storageService.getSubscriptions();
        assertEquals(1, subscriptions.size());
        assertSubscriptionEquals(new Subscription(1L, "provider-1", "data-1"), subscriptions.iterator().next());
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

        DynamoDbReadResponse readResponse = dynamoDbService.read();
        assertEquals(3, readResponse.consumedCapacity());

        Map<String, List<DynamoDbRecord>> records = readResponse.records()
                .stream()
                .collect(Collectors.groupingBy(DynamoDbRecord::type));
        assertEquals(1, records.get("command").size());
        assertFalse(records.get("settings").isEmpty());
        assertEquals(2, records.get("settings").size() + records.get("invalid").size()); // it is undefined, which records exactly are cleaned up

        assertEquals(1, storageService.cleanup(2));
        assertEquals(1, storageService.flush());

        readResponse = dynamoDbService.read();
        assertEquals(2, readResponse.consumedCapacity());

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

    private static void assertDynamoDbRecordEquals(final DynamoDbRecord expected, final DynamoDbRecord actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.subject(), actual.subject());
        assertEquals(expected.time(), actual.time());
        assertEquals(new String(ZipUtils.decompress(expected.data())), new String(ZipUtils.decompress(actual.data())));
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

    private static void assertSubscriptionEquals(final Subscription expected, final Subscription actual) {
        assertEquals(expected.chatId(), actual.chatId());
        assertEquals(expected.provider(), actual.provider());
        assertEquals(expected.data(), actual.data());
    }

}
