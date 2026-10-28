package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.CommandProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SettingsProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.response.Chat;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.response.Message;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.response.Update;
import com.lonebytesoft.hamster.eventnotifybot.service.core.CommandParsingService;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.StorageUtils;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.ApplicationContextMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.DynamoDbServiceMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.ProviderMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.TelegramApiMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collection;
import java.util.List;

import static com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils.compress;
import static com.lonebytesoft.hamster.eventnotifybot.test.Assertions.assertDynamoDbRecordsEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventNotifyBotJobHandlerTest {

    private static final int WRITE_COST_LIMIT = 5;

    private final DynamoDbServiceMock dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final InstantSource instantSource = InstantSource.fixed(Instant.parse("2026-01-01T12:00:00Z"));
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper, instantSource, WRITE_COST_LIMIT);
    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);
    private final Collection<Provider> providers = List.of(
            new ProviderMock("mock-first", "data-first", Instant.parse("2026-01-01T18:00:00Z"), "image-url-first", "view-first"),
            new ProviderMock("mock-second", "data-second", Instant.parse("2026-01-01T20:00:00Z"), "image-url-second", "view-second")
    );
    private final CommandParsingService commandParsingService = new CommandParsingService(providers, null);
    private final EventNotifyBotJobHandler handler = new EventNotifyBotJobHandler(new ApplicationContextMock(
            storageService,
            telegramService,
            commandParsingService,
            instantSource,
            providers,
            WRITE_COST_LIMIT
    ));

    private final StorageUtils storageUtils = new StorageUtils(dynamoDbService, jsonMapper);

    @BeforeEach
    public void beforeEach() {
        dynamoDbService.reset();
        telegramApi.reset();
    }

    /*
    Given: no commands in storage, no Telegram updates.
    Expected:
    - settings saved in storage with empty offset.
     */
    @Test
    public void run_noCommandsNoMessages_onlySettingsUpdated() throws Exception {
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(null)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates.
    Expected:
    - settings not updated after multiple runs.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExist_settingsNotUpdated() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))))
        ));
        telegramApi.setUpdates(List.of());

        for (int i = 0; i < 3; i++) {
            storageService.fetch();
            handler.run(Duration.ZERO);
        }

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: 2 commands in storage, no Telegram updates.
    Expected:
    - settings saved in storage with empty offset,
    - commands deleted from storage,
    - 2 messages sent in order of command time.
     */
    @Test
    public void run_fewCommandsNoMessages_commandsExecuted() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "command", "11", 102L, serialize(new CommandProperties("unknown", List.of("first"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "command", "12", 101L, serialize(new CommandProperties("unknown", List.of("second")))))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(null)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(12L, null, "Unrecognized command: <b>second</b>"),
                new TelegramApiMock.SentMessage(11L, null, "Unrecognized command: <b>first</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 2 commands and settings in storage, no Telegram updates.
    Expected:
    - settings not updated,
    - commands deleted from storage,
    - 2 messages sent in order of command time.
     */
    @Test
    public void run_fewCommandsNoMessagesSettingsExist_commandsExecutedSettingsNotChanged() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "command", "11", 102L, serialize(new CommandProperties("unknown", List.of("first"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "command", "12", 101L, serialize(new CommandProperties("unknown", List.of("second"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "settings", null, 103L, serialize(new SettingsProperties(1001L))))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("3", "settings", null, 103L, serialize(new SettingsProperties(1001L)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(12L, null, "Unrecognized command: <b>second</b>"),
                new TelegramApiMock.SentMessage(11L, null, "Unrecognized command: <b>first</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 6 commands in storage (more than the expected limit), no Telegram updates.
    Expected:
    - settings not saved in storage,
    - 5 oldest commands deleted from storage, 1 left,
    - 5 messages corresponding to 5 deleted commands sent in order of command time.
     */
    @Test
    public void run_tooManyCommandsNoMessages_someCommandsExecuted() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "command", "11", 106L, serialize(new CommandProperties("unknown", List.of("first"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "command", "12", 105L, serialize(new CommandProperties("unknown", List.of("second"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "command", "13", 104L, serialize(new CommandProperties("unknown", List.of("third"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "command", "14", 103L, serialize(new CommandProperties("unknown", List.of("fourth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "command", "15", 102L, serialize(new CommandProperties("unknown", List.of("fifth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("6", "command", "16", 101L, serialize(new CommandProperties("unknown", List.of("sixth")))))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "command", "11", 106L, serialize(new CommandProperties("unknown", List.of("first"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(16L, null, "Unrecognized command: <b>sixth</b>"),
                new TelegramApiMock.SentMessage(15L, null, "Unrecognized command: <b>fifth</b>"),
                new TelegramApiMock.SentMessage(14L, null, "Unrecognized command: <b>fourth</b>"),
                new TelegramApiMock.SentMessage(13L, null, "Unrecognized command: <b>third</b>"),
                new TelegramApiMock.SentMessage(12L, null, "Unrecognized command: <b>second</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 3 commands in storage having the same chat id, no Telegram updates.
    Expected:
    - settings saved in storage with empty offset,
    - 1 oldest command deleted from storage, 2 left,
    - 1 message corresponding to the deleted command sent.
     */
    @Test
    public void run_fewCommandsAndNoMessagesFromSameChat_oneCommandExecuted() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "command", "11", 103L, serialize(new CommandProperties("unknown", List.of("first"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "command", "11", 102L, serialize(new CommandProperties("unknown", List.of("second"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "command", "11", 101L, serialize(new CommandProperties("unknown", List.of("third")))))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only the most recent command processed
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(null))),
                new DynamoDbRecord("2", "command", "11", 102L, serialize(new CommandProperties("unknown", List.of("second")))),
                new DynamoDbRecord("1", "command", "11", 103L, serialize(new CommandProperties("unknown", List.of("first"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(11L, null, "Unrecognized command: <b>third</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 6 commands in storage having the same chat id (more than the expected limit), no Telegram updates.
    Expected:
    - settings saved in storage with empty offset,
    - 1 oldest command deleted from storage, 5 left,
    - 1 message corresponding to the deleted command sent.
     */
    @Test
    public void run_tooManyCommandsAndNoMessagesFromSameChat_oneCommandExecuted() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "command", "11", 106L, serialize(new CommandProperties("unknown", List.of("first"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "command", "11", 105L, serialize(new CommandProperties("unknown", List.of("second"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "command", "11", 104L, serialize(new CommandProperties("unknown", List.of("third"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "command", "11", 103L, serialize(new CommandProperties("unknown", List.of("fourth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "command", "11", 102L, serialize(new CommandProperties("unknown", List.of("fifth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("6", "command", "11", 101L, serialize(new CommandProperties("unknown", List.of("sixth")))))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only the oldest command processed
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(null))),
                new DynamoDbRecord("5", "command", "11", 102L, serialize(new CommandProperties("unknown", List.of("fifth")))),
                new DynamoDbRecord("4", "command", "11", 103L, serialize(new CommandProperties("unknown", List.of("fourth")))),
                new DynamoDbRecord("3", "command", "11", 104L, serialize(new CommandProperties("unknown", List.of("third")))),
                new DynamoDbRecord("2", "command", "11", 105L, serialize(new CommandProperties("unknown", List.of("second")))),
                new DynamoDbRecord("1", "command", "11", 106L, serialize(new CommandProperties("unknown", List.of("first"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(11L, null, "Unrecognized command: <b>sixth</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, 2 Telegram updates.
    Expected:
    - settings saved in storage with the latest update offset,
    - 2 messages sent in order of message id.
     */
    @Test
    public void run_noCommandsFewMessages_messagesProcessed() throws Exception {
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1002L, "private"), "/second unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(2L)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1002L, null, "Unrecognized command: <b>second</b>"),
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>first</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 2 commands in storage, 2 Telegram updates.
    Expected:
    - settings saved in storage with the biggest offset,
    - commands deleted from storage,
    - 4 messages sent: 2 corresponding to commands in order of command time, then 2 corresponding to Telegram updates in order of message id.
     */
    @Test
    public void run_fewCommandsFewMessages_commandsExecutedThenMessagesProcessed() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "command", "13", 104L, serialize(new CommandProperties("unknown", List.of("third"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "command", "14", 103L, serialize(new CommandProperties("unknown", List.of("fourth")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1002L, "private"), "/second unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(2L)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(14L, null, "Unrecognized command: <b>fourth</b>"),
                new TelegramApiMock.SentMessage(13L, null, "Unrecognized command: <b>third</b>"),
                new TelegramApiMock.SentMessage(1002L, null, "Unrecognized command: <b>second</b>"),
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>first</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 2 commands and settings in storage, 2 Telegram updates.
    Expected:
    - settings updated with the biggest offset,
    - commands deleted from storage,
    - 4 messages sent: 2 corresponding to commands in order of command time, then 2 corresponding to Telegram updates in order of message id.
     */
    @Test
    public void run_fewCommandsFewMessagesSettingsExist_commandsExecutedThenMessagesProcessedSettingsUpdated() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "command", "13", 104L, serialize(new CommandProperties("unknown", List.of("third"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "command", "14", 103L, serialize(new CommandProperties("unknown", List.of("fourth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "settings", null, 103L, serialize(new SettingsProperties(10000L))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(10001L, new Message(11L, 101L, new Chat(1002L, "private"), "/second unknown")),
                new Update(10002L, new Message(12L, 102L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("5", "settings", null, null, serialize(new SettingsProperties(10002L)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(14L, null, "Unrecognized command: <b>fourth</b>"),
                new TelegramApiMock.SentMessage(13L, null, "Unrecognized command: <b>third</b>"),
                new TelegramApiMock.SentMessage(1002L, null, "Unrecognized command: <b>second</b>"),
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>first</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 6 commands in storage (more than the expected limit), 2 Telegram updates.
    Expected:
    - settings not saved in storage,
    - 5 oldest commands deleted from storage, 1 left,
    - 5 messages corresponding to deleted commands sent in order of command time.
     */
    @Test
    public void run_tooManyCommandsFewMessages_someCommandsExecutedNoMessagesProcessed() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "command", "13", 106L, serialize(new CommandProperties("unknown", List.of("third"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "command", "14", 105L, serialize(new CommandProperties("unknown", List.of("fourth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "command", "15", 104L, serialize(new CommandProperties("unknown", List.of("fifth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("6", "command", "16", 103L, serialize(new CommandProperties("unknown", List.of("sixth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "command", "17", 102L, serialize(new CommandProperties("unknown", List.of("seventh"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("8", "command", "18", 101L, serialize(new CommandProperties("unknown", List.of("eighth")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 107L, new Chat(1002L, "private"), "/unknown second")),
                new Update(2L, new Message(12L, 108L, new Chat(1001L, "private"), "/unknown first"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                // no settings updated because commands exhausted the whole budget
                new DynamoDbRecord("3", "command", "13", 106L, serialize(new CommandProperties("unknown", List.of("third")))) // the most recent command exceeds budget and stays in storage
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(18L, null, "Unrecognized command: <b>eighth</b>"),
                new TelegramApiMock.SentMessage(17L, null, "Unrecognized command: <b>seventh</b>"),
                new TelegramApiMock.SentMessage(16L, null, "Unrecognized command: <b>sixth</b>"),
                new TelegramApiMock.SentMessage(15L, null, "Unrecognized command: <b>fifth</b>"),
                new TelegramApiMock.SentMessage(14L, null, "Unrecognized command: <b>fourth</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, 3 Telegram updates from the same chat.
    Expected:
    - settings saved in storage with the biggest offset,
    - 2 commands corresponding to the most recent messages saved to storage,
    - 1 message corresponding to the oldest message sent.
     */
    @Test
    public void run_noCommandsAndFewMessagesFromSameChat_oneMessageProcessed() throws Exception {
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1001L, "private"), "/third unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/second unknown")),
                new Update(3L, new Message(13L, 103L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only the most recent message processed
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(3L))),
                new DynamoDbRecord(null, "command", "1001", 103L, serialize(new CommandProperties("unknown", List.of("first")))),
                new DynamoDbRecord(null, "command", "1001", 102L, serialize(new CommandProperties("unknown", List.of("second"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>third</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 2 commands in storage, 2 Telegram updates, all from the same chat.
    Expected:
    - settings saved in storage with the biggest offset,
    - 1 oldest command deleted from storage, 2 commands corresponding to the messages saved to storage,
    - 1 message corresponding to the oldest command sent.
     */
    @Test
    public void run_fewCommandsAndFewMessagesFromSameChat_oneCommandExecuted() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "command", "1001", 104L, serialize(new CommandProperties("unknown", List.of("fourth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "command", "1001", 103L, serialize(new CommandProperties("unknown", List.of("third")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1001L, "private"), "/second unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only the most recent command processed
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(2L))),
                new DynamoDbRecord(null, "command", "1001", 101L, serialize(new CommandProperties("unknown", List.of("second")))),
                new DynamoDbRecord(null, "command", "1001", 102L, serialize(new CommandProperties("unknown", List.of("first")))),
                new DynamoDbRecord("3", "command", "1001", 104L, serialize(new CommandProperties("unknown", List.of("fourth"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>third</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 6 commands in storage, 2 Telegram updates, all from the same chat.
    Expected:
    - settings saved in storage with the biggest offset,
    - 1 oldest command deleted from storage, 2 commands corresponding to the messages saved to storage,
    - 1 message corresponding to the oldest command sent.
     */
    @Test
    public void run_tooManyCommandsAndFewMessagesFromSameChat_oneCommandExecuted() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "command", "1001", 108L, serialize(new CommandProperties("unknown", List.of("eighth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "command", "1001", 107L, serialize(new CommandProperties("unknown", List.of("seventh"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "command", "1001", 106L, serialize(new CommandProperties("unknown", List.of("sixth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("6", "command", "1001", 105L, serialize(new CommandProperties("unknown", List.of("fifth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "command", "1001", 104L, serialize(new CommandProperties("unknown", List.of("fourth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("8", "command", "1001", 103L, serialize(new CommandProperties("unknown", List.of("third")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1001L, "private"), "/second unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only the most recent command processed
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(2L))),
                new DynamoDbRecord(null, "command", "1001", 101L, serialize(new CommandProperties("unknown", List.of("second")))),
                new DynamoDbRecord(null, "command", "1001", 102L, serialize(new CommandProperties("unknown", List.of("first")))),
                new DynamoDbRecord("3", "command", "1001", 108L, serialize(new CommandProperties("unknown", List.of("eighth")))),
                new DynamoDbRecord("4", "command", "1001", 107L, serialize(new CommandProperties("unknown", List.of("seventh")))),
                new DynamoDbRecord("5", "command", "1001", 106L, serialize(new CommandProperties("unknown", List.of("sixth")))),
                new DynamoDbRecord("6", "command", "1001", 105L, serialize(new CommandProperties("unknown", List.of("fifth")))),
                new DynamoDbRecord("7", "command", "1001", 104L, serialize(new CommandProperties("unknown", List.of("fourth"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>third</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, 6 Telegram updates (more than the expected limit).
    Expected:
    - settings saved in storage with the offset corresponding to 4 processed messages (budget 5: 1 to save settings + potentially 4 for 4 processed messages),
    - 4 messages corresponding to the oldest updates in order of message time sent.
     */
    @Test
    public void run_noCommandsTooManyMessages_someMessagesProcessed() throws Exception {
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1006L, "private"), "/sixth unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1005L, "private"), "/fifth unknown")),
                new Update(3L, new Message(13L, 103L, new Chat(1004L, "private"), "/fourth unknown")),
                new Update(4L, new Message(14L, 104L, new Chat(1003L, "private"), "/third unknown")),
                new Update(5L, new Message(15L, 105L, new Chat(1002L, "private"), "/second unknown")),
                new Update(6L, new Message(16L, 106L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only four messages processed due to cautious budget spending
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(4L)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1006L, null, "Unrecognized command: <b>sixth</b>"),
                new TelegramApiMock.SentMessage(1005L, null, "Unrecognized command: <b>fifth</b>"),
                new TelegramApiMock.SentMessage(1004L, null, "Unrecognized command: <b>fourth</b>"),
                new TelegramApiMock.SentMessage(1003L, null, "Unrecognized command: <b>third</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 2 commands in storage, 6 Telegram updates (more than the expected limit).
    Expected:
    - settings saved in storage the offset corresponding to 2 processed messages (budget 5: 2 to process commands + 1 to save settings + potentially 2 for 2 processed messages),
    - commands deleted from storage,
    - 2 messages corresponding to the commands in order of command time, then 2 messages corresponding to the oldest updates in order of message time sent.
     */
    @Test
    public void run_fewCommandsTooManyMessages_commandsExecutedSomeMessagesProcessed() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "command", "17", 108L, serialize(new CommandProperties("unknown", List.of("seventh"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("8", "command", "18", 107L, serialize(new CommandProperties("unknown", List.of("eighth")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1006L, "private"), "/sixth unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1005L, "private"), "/fifth unknown")),
                new Update(3L, new Message(13L, 103L, new Chat(1004L, "private"), "/fourth unknown")),
                new Update(4L, new Message(14L, 104L, new Chat(1003L, "private"), "/third unknown")),
                new Update(5L, new Message(15L, 105L, new Chat(1002L, "private"), "/second unknown")),
                new Update(6L, new Message(16L, 106L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only two messages processed due to cautious budget spending
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(2L)))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(18L, null, "Unrecognized command: <b>eighth</b>"),
                new TelegramApiMock.SentMessage(17L, null, "Unrecognized command: <b>seventh</b>"),
                new TelegramApiMock.SentMessage(1006L, null, "Unrecognized command: <b>sixth</b>"),
                new TelegramApiMock.SentMessage(1005L, null, "Unrecognized command: <b>fifth</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 6 commands in storage, 6 Telegram updates (both more than the expected limit).
    Expected:
    - settings not saved in storage,
    - 5 oldest commands deleted from storage,
    - 5 messages corresponding to the deleted commands sent in order of command time.
     */
    @Test
    public void run_tooManyCommandsTooManyMessages_someCommandsExecutedNoMessagesProcessed() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "command", "17", 112L, serialize(new CommandProperties("unknown", List.of("seventh"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("8", "command", "18", 111L, serialize(new CommandProperties("unknown", List.of("eighth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("9", "command", "19", 110L, serialize(new CommandProperties("unknown", List.of("ninth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("10", "command", "20", 109L, serialize(new CommandProperties("unknown", List.of("tenth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "command", "21", 108L, serialize(new CommandProperties("unknown", List.of("eleventh"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "command", "22", 107L, serialize(new CommandProperties("unknown", List.of("twelfth")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1006L, "private"), "/sixth unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1005L, "private"), "/fifth unknown")),
                new Update(3L, new Message(13L, 103L, new Chat(1004L, "private"), "/fourth unknown")),
                new Update(4L, new Message(14L, 104L, new Chat(1003L, "private"), "/third unknown")),
                new Update(5L, new Message(15L, 105L, new Chat(1002L, "private"), "/second unknown")),
                new Update(6L, new Message(16L, 106L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                // no settings updated because commands exhausted the whole budget
                new DynamoDbRecord("7", "command", "17", 112L, serialize(new CommandProperties("unknown", List.of("seventh")))) // the most recent command exceeds budget and stays in storage
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(22L, null, "Unrecognized command: <b>twelfth</b>"),
                new TelegramApiMock.SentMessage(21L, null, "Unrecognized command: <b>eleventh</b>"),
                new TelegramApiMock.SentMessage(20L, null, "Unrecognized command: <b>tenth</b>"),
                new TelegramApiMock.SentMessage(19L, null, "Unrecognized command: <b>ninth</b>"),
                new TelegramApiMock.SentMessage(18L, null, "Unrecognized command: <b>eighth</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, 6 Telegram updates for the same chat (more than the expected limit).
    Expected:
    - settings saved in storage with the offset corresponding to 4 processed messages (budget 5: 1 to save settings + potentially 4 for 4 processed messages),
    - 3 commands saved to storage (the next oldest 3 after a message for the oldest update was processed),
    - 1 message corresponding to the oldest update sent.
     */
    @Test
    public void run_noCommandsAndTooManyMessagesFromSameChat_oneMessageProcessed() throws Exception {
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1001L, "private"), "/sixth unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/fifth unknown")),
                new Update(3L, new Message(13L, 103L, new Chat(1001L, "private"), "/fourth unknown")),
                new Update(4L, new Message(14L, 104L, new Chat(1001L, "private"), "/third unknown")),
                new Update(5L, new Message(15L, 105L, new Chat(1001L, "private"), "/second unknown")),
                new Update(6L, new Message(16L, 106L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only four messages processed due to cautious budget spending
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(4L))),
                new DynamoDbRecord(null, "command", "1001", 102L, serialize(new CommandProperties("unknown", List.of("fifth")))),
                new DynamoDbRecord(null, "command", "1001", 103L, serialize(new CommandProperties("unknown", List.of("fourth")))),
                new DynamoDbRecord(null, "command", "1001", 104L, serialize(new CommandProperties("unknown", List.of("third"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>sixth</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 2 commands in storage, 6 Telegram updates (more than the expected limit), all for the same chat.
    Expected:
    - settings saved in storage with the offset corresponding to 3 processed updates (budget 5: 1 to process command + 0 to process skipped command + 1 to save settings + potentially 3 for 3 processed messages),
    - 1 oldest command removed from storage,
    - 3 commands corresponding to 3 oldest updates saved to storage,
    - 1 message corresponding to the oldest command sent.
     */
    @Test
    public void run_fewCommandsAndTooManyMessagesFromSameChat_oneCommandExecuted() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "command", "1001", 108L, serialize(new CommandProperties("unknown", List.of("seventh"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("8", "command", "1001", 107L, serialize(new CommandProperties("unknown", List.of("eighth")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1001L, "private"), "/sixth unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/fifth unknown")),
                new Update(3L, new Message(13L, 103L, new Chat(1001L, "private"), "/fourth unknown")),
                new Update(4L, new Message(14L, 104L, new Chat(1001L, "private"), "/third unknown")),
                new Update(5L, new Message(15L, 105L, new Chat(1001L, "private"), "/second unknown")),
                new Update(6L, new Message(16L, 106L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        // only two messages processed due to cautious budget spending
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(3L))),
                new DynamoDbRecord("7", "command", "1001", 108L, serialize(new CommandProperties("unknown", List.of("seventh")))),
                new DynamoDbRecord(null, "command", "1001", 101L, serialize(new CommandProperties("unknown", List.of("sixth")))),
                new DynamoDbRecord(null, "command", "1001", 102L, serialize(new CommandProperties("unknown", List.of("fifth")))),
                new DynamoDbRecord(null, "command", "1001", 103L, serialize(new CommandProperties("unknown", List.of("fourth"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>eighth</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: 6 commands in storage, 6 Telegram updates (both more than the expected limit), all for the same chat.
    Expected:
    - settings saved in storage with the offset corresponding to 3 processed updates (budget 5: 1 to process command + 0 to process 5 skipped commands + 1 to save settings + potentially 3 for 3 processed messages),
    - 1 oldest command removed from storage,
    - 3 commands corresponding to 3 oldest updates saved to storage,
    - 1 message corresponding to the oldest command sent.
     */
    @Test
    public void run_tooManyCommandsAndTooManyMessagesFromSameChat_oneCommandExecuted() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "command", "1001", 112L, serialize(new CommandProperties("unknown", List.of("seventh"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("8", "command", "1001", 111L, serialize(new CommandProperties("unknown", List.of("eighth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("9", "command", "1001", 110L, serialize(new CommandProperties("unknown", List.of("ninth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("10", "command", "1001", 109L, serialize(new CommandProperties("unknown", List.of("tenth"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "command", "1001", 108L, serialize(new CommandProperties("unknown", List.of("eleventh"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "command", "1001", 107L, serialize(new CommandProperties("unknown", List.of("twelfth")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1001L, "private"), "/sixth unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/fifth unknown")),
                new Update(3L, new Message(13L, 103L, new Chat(1001L, "private"), "/fourth unknown")),
                new Update(4L, new Message(14L, 104L, new Chat(1001L, "private"), "/third unknown")),
                new Update(5L, new Message(15L, 105L, new Chat(1001L, "private"), "/second unknown")),
                new Update(6L, new Message(16L, 106L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(3L))),
                new DynamoDbRecord("7", "command", "1001", 112L, serialize(new CommandProperties("unknown", List.of("seventh")))),
                new DynamoDbRecord("8", "command", "1001", 111L, serialize(new CommandProperties("unknown", List.of("eighth")))),
                new DynamoDbRecord("9", "command", "1001", 110L, serialize(new CommandProperties("unknown", List.of("ninth")))),
                new DynamoDbRecord("10", "command", "1001", 109L, serialize(new CommandProperties("unknown", List.of("tenth")))),
                new DynamoDbRecord("11", "command", "1001", 108L, serialize(new CommandProperties("unknown", List.of("eleventh")))),
                new DynamoDbRecord(null, "command", "1001", 101L, serialize(new CommandProperties("unknown", List.of("sixth")))),
                new DynamoDbRecord(null, "command", "1001", 102L, serialize(new CommandProperties("unknown", List.of("fifth")))),
                new DynamoDbRecord(null, "command", "1001", 103L, serialize(new CommandProperties("unknown", List.of("fourth"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>twelfth</b>")
        ), telegramApi.getSentMessages());
    }

    @Test
    public void run_noCommandsOneMessageUnknownCost_noCommandsExecutedOneMessageProcessed() throws Exception {
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1001L, "private"), "/show mock-first"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(1L))),
                new DynamoDbRecord(null, "provider_state", "mock-first", Instant.parse("2026-01-01T18:00:00Z").getEpochSecond(), compress("data-first".getBytes())),
                new DynamoDbRecord(null, "command", "1001", 101L, serialize(new CommandProperties("show", List.of("mock-first"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void run_oneCommandWithinBudgetOneMessageUnknownCost_oneCommandExecutedNoMessagesProcessed() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "command", "1002", 102L, serialize(new CommandProperties("show", List.of("mock-second")))))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1001L, "private"), "/show mock-first"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(1L))),
                new DynamoDbRecord(null, "provider_state", "mock-second", Instant.parse("2026-01-01T20:00:00Z").getEpochSecond(), compress("data-second".getBytes())),
                new DynamoDbRecord(null, "command", "1001", 101L, serialize(new CommandProperties("show", List.of("mock-first")))),
                new DynamoDbRecord(null, "command", "1002", 102L, serialize(new CommandProperties("show", List.of("mock-second"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: 2 commands in storage, no Telegram updates, stale provider state with subscriptions.
    Expected:
    - commands processed,
    - provider state not updated.
     */
    @Test
    public void run_fewCommandsNoMessagesStaleProviderStateWithSubscriptions_commandsExecutedNoProviderStateUpdated() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "command", "11", 102L, serialize(new CommandProperties("unknown", List.of("first"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "command", "12", 101L, serialize(new CommandProperties("unknown", List.of("second"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription", "13", 103L, serialize(new SubscriptionProperties("5")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "subscription_cache", "mock-first", 104L, serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(null))),
                new DynamoDbRecord("3", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of())),
                new DynamoDbRecord("4", "subscription", "13", 103L, serialize(new SubscriptionProperties("5"))),
                new DynamoDbRecord("5", "subscription_cache", "mock-first", 104L, serialize(List.of()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(12L, null, "Unrecognized command: <b>second</b>"),
                new TelegramApiMock.SentMessage(11L, null, "Unrecognized command: <b>first</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, 2 Telegram updates, stale provider state with subscriptions.
    Expected:
    - messages processed,
    - provider state not updated.
     */
    @Test
    public void run_noCommandsFewMessagesStaleProviderStateWithSubscriptions_messagesProcessedNoProviderStateUpdated() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription", "13", 103L, serialize(new SubscriptionProperties("5")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "subscription_cache", "mock-first", 104L, serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of(
                new Update(1L, new Message(11L, 101L, new Chat(1002L, "private"), "/second unknown")),
                new Update(2L, new Message(12L, 102L, new Chat(1001L, "private"), "/first unknown"))
        ));

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "settings", null, null, serialize(new SettingsProperties(2L))),
                new DynamoDbRecord("3", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of())),
                new DynamoDbRecord("4", "subscription", "13", 103L, serialize(new SubscriptionProperties("5"))),
                new DynamoDbRecord("5", "subscription_cache", "mock-first", 104L, serialize(List.of()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1002L, null, "Unrecognized command: <b>second</b>"),
                new TelegramApiMock.SentMessage(1001L, null, "Unrecognized command: <b>first</b>")
        ), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, actual provider state with subscriptions.
    Expected:
    - nothing is updated.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistActualProviderStateWithSubscriptions_nothingUpdated() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize(List.of())),
                new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4"))),
                new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, actual provider state without subscriptions.
    Expected:
    - nothing is updated.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistActualProviderStateWithoutSubscriptions_nothingUpdated() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize(List.of()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, stale provider state with subscriptions.
    Expected:
    - provider state is updated,
    - subscription updated,
    - update message sent.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistStaleProviderStateWithSubscriptions_providerStateUpdatedUpdateSent() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        final long providerTime = Instant.parse("2026-01-01T18:00:00Z").getEpochSecond();
        final long subscriptionTime = Instant.parse("2026-01-01T12:00:00Z").getEpochSecond();
        final String cacheRef = storageUtils.getSubscriptionCacheRefs().get("3");
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", providerTime, compress("data-first".getBytes())),
                new DynamoDbRecord("3", "subscription", "11", subscriptionTime, serialize(new SubscriptionProperties(cacheRef))),
                new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of())),
                new DynamoDbRecord(cacheRef, "subscription_cache", "mock-first", subscriptionTime, compress("data-first".getBytes()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                11L,
                "image-url-first",
                """
                An update from <b>mock-first</b>!
                
                view-first
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, stale provider state without subscriptions.
    Expected:
    - nothing is updated.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistStaleProviderStateWithoutSubscriptions_nothingUpdated() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, non-updatable provider state with subscriptions.
    Expected:
    - nothing is updated.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistNonUpdatableProviderStateWithSubscriptions_nothingUpdated() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", null, serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", null, serialize(List.of())),
                new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4"))),
                new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, no provider state, provider with subscriptions.
    Expected:
    - provider state is created,
    - subscription updated,
    - update message sent.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistNonExistentProviderStateWithSubscriptions_providerStateCreatedUpdateSent() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "subscription", "11", 102L, serialize(new SubscriptionProperties("3")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "subscription_cache", "mock-first", 103L, serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        final long providerTime = Instant.parse("2026-01-01T18:00:00Z").getEpochSecond();
        final long subscriptionTime = Instant.parse("2026-01-01T12:00:00Z").getEpochSecond();
        final String cacheRef = storageUtils.getSubscriptionCacheRefs().get("2");
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))),
                new DynamoDbRecord(null, "provider_state", "mock-first", providerTime, compress("data-first".getBytes())),
                new DynamoDbRecord("2", "subscription", "11", subscriptionTime, serialize(new SubscriptionProperties(cacheRef))),
                new DynamoDbRecord("3", "subscription_cache", "mock-first", 103L, serialize(List.of())),
                new DynamoDbRecord(cacheRef, "subscription_cache", "mock-first", subscriptionTime, compress("data-first".getBytes()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                11L,
                "image-url-first",
                """
                An update from <b>mock-first</b>!
                
                view-first
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, two stale provider states with subscriptions.
    Expected:
    - the earliest provider state is updated,
    - no update messages sent.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistTwoStaleProviderStatesWithSubscriptions_earliestProviderStateUpdatedNoUpdatesSent() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "provider_state", "mock-second", Instant.parse("2026-01-01T10:00:00Z").getEpochSecond(), serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("6", "subscription", "12", 104L, serialize(new SubscriptionProperties("7")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "subscription_cache", "mock-second", 105L, serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of())),
                new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4"))),
                new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of())),
                new DynamoDbRecord("5", "provider_state", "mock-second", Instant.parse("2026-01-01T20:00:00Z").getEpochSecond(), compress("data-second".getBytes())),
                new DynamoDbRecord("6", "subscription", "12", 104L, serialize(new SubscriptionProperties("7"))),
                new DynamoDbRecord("7", "subscription_cache", "mock-second", 105L, serialize(List.of()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, two stale provider states with subscriptions.
    Expected:
    - the earliest provider state is updated,
    - no update messages sent.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistOneStaleOneNonExistentProviderStatesWithSubscriptions_missingProviderStateCreatedNoUpdatesSent() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "subscription", "12", 104L, serialize(new SubscriptionProperties("6")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("6", "subscription_cache", "mock-second", 105L, serialize(List.of())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 101L, serialize(new SettingsProperties(1001L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(List.of())),
                new DynamoDbRecord("3", "subscription", "11", 102L, serialize(new SubscriptionProperties("4"))),
                new DynamoDbRecord("4", "subscription_cache", "mock-first", 103L, serialize(List.of())),
                new DynamoDbRecord(null, "provider_state", "mock-second", Instant.parse("2026-01-01T20:00:00Z").getEpochSecond(), compress("data-second".getBytes())),
                new DynamoDbRecord("5", "subscription", "12", 104L, serialize(new SubscriptionProperties("6"))),
                new DynamoDbRecord("6", "subscription_cache", "mock-second", 105L, serialize(List.of()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, one stale and one actual provider states, both with subscriptions.
    Expected:
    - the stale provider state is updated,
    - three update messages sent with the stale provider updates, the other two belonging to the same provider not sent,
    - provider updates belonging to the actual provider state not sent.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistOneStaleOneActualProviderStatesWithSubscriptions_providerStateUpdatedUpdatesSentWithinBudget() throws Exception {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 100L, serialize(new SettingsProperties(1000L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), compress("data-first-old".getBytes()))), // stale provider state
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "provider_state", "mock-second", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), compress("data-second-old".getBytes()))), // actual provider state
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription", "11", 101L, serialize(new SubscriptionProperties("9")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "subscription", "12", 102L, serialize(new SubscriptionProperties("9")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("6", "subscription", "13", 103L, serialize(new SubscriptionProperties("9")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "subscription", "14", 104L, serialize(new SubscriptionProperties("9")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("8", "subscription", "15", 105L, serialize(new SubscriptionProperties("9")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("9", "subscription_cache", "mock-first", 106L, compress("data-first-old".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("10", "subscription", "16", 107L, serialize(new SubscriptionProperties("12")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "subscription", "17", 108L, serialize(new SubscriptionProperties("12")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "mock-second", 109L, compress("data-second-old".getBytes())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        final long time = Instant.parse("2026-01-01T12:00:00Z").getEpochSecond();
        final String cacheRef = storageUtils.getSubscriptionCacheRefs().get("4");
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 100L, serialize(new SettingsProperties(1000L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", Instant.parse("2026-01-01T18:00:00Z").getEpochSecond(), compress("data-first".getBytes())), // updated provider state
                new DynamoDbRecord("3", "provider_state", "mock-second", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), compress("data-second-old".getBytes())), // the same provider state
                new DynamoDbRecord("4", "subscription", "11", time, serialize(new SubscriptionProperties(cacheRef))), // updated subscription state
                new DynamoDbRecord("5", "subscription", "12", time, serialize(new SubscriptionProperties(cacheRef))), // updated subscription state
                new DynamoDbRecord("6", "subscription", "13", time, serialize(new SubscriptionProperties(cacheRef))), // updated subscription state
                new DynamoDbRecord("7", "subscription", "14", 104L, serialize(new SubscriptionProperties("9"))), // old subscription state (not enough budget to update)
                new DynamoDbRecord("8", "subscription", "15", 105L, serialize(new SubscriptionProperties("9"))), // old subscription state (not enough budget to update)
                new DynamoDbRecord("9", "subscription_cache", "mock-first", 106L, compress("data-first-old".getBytes())), // old cache entry
                new DynamoDbRecord(cacheRef, "subscription_cache", "mock-first", time, compress("data-first".getBytes())), // new cache entry
                new DynamoDbRecord("10", "subscription", "16", 107L, serialize(new SubscriptionProperties("12"))),
                new DynamoDbRecord("11", "subscription", "17", 108L, serialize(new SubscriptionProperties("12"))),
                new DynamoDbRecord("12", "subscription_cache", "mock-second", 109L, compress("data-second-old".getBytes()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(
                        11L,
                        "image-url-first",
                        """
                        An update from <b>mock-first</b>!
                        
                        view-first
                        """.trim().stripIndent()
                ),
                new TelegramApiMock.SentMessage(
                        12L,
                        "image-url-first",
                        """
                        An update from <b>mock-first</b>!
                        
                        view-first
                        """.trim().stripIndent()
                ),
                new TelegramApiMock.SentMessage(
                        13L,
                        "image-url-first",
                        """
                        An update from <b>mock-first</b>!
                        
                        view-first
                        """.trim().stripIndent()
                )
        ), telegramApi.getSentMessages());
    }

    /*
    Given: no commands in storage, settings exist, no Telegram updates, two actual provider states, both with subscriptions.
    Expected:
    - update messages sent, one per chat.
     */
    @Test
    public void run_noCommandsNoMessagesSettingsExistTwoActualProviderStatesWithSubscriptions_updatesSentOnePerChat() throws Exception {
        final long providerTime = Instant.parse("2026-01-01T13:00:00Z").getEpochSecond();
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "settings", null, 100L, serialize(new SettingsProperties(1000L)))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "provider_state", "mock-first", providerTime, compress("data-first".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "provider_state", "mock-second", providerTime, compress("data-second".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription", "11", 101L, serialize(new SubscriptionProperties("6")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("5", "subscription", "12", 102L, serialize(new SubscriptionProperties("6")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("6", "subscription_cache", "mock-first", 103L, compress("data-first-old".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("7", "subscription_cache", "mock-first", 104L, compress("data-first".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("8", "subscription", "11", 105L, serialize(new SubscriptionProperties("10")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("9", "subscription", "13", 106L, serialize(new SubscriptionProperties("10")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("10", "subscription_cache", "mock-second", 107L, compress("data-second-old".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "subscription_cache", "mock-second", 108L, compress("data-second".getBytes())))
        ));
        telegramApi.setUpdates(List.of());

        storageService.fetch();
        handler.run(Duration.ZERO);

        final long subscriptionTime = Instant.parse("2026-01-01T12:00:00Z").getEpochSecond();
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "settings", null, 100L, serialize(new SettingsProperties(1000L))),
                new DynamoDbRecord("2", "provider_state", "mock-first", providerTime, compress("data-first".getBytes())),
                new DynamoDbRecord("3", "provider_state", "mock-second", providerTime, compress("data-second".getBytes())),
                new DynamoDbRecord("4", "subscription", "11", subscriptionTime, serialize(new SubscriptionProperties("7"))), // updated subscription state
                new DynamoDbRecord("5", "subscription", "12", subscriptionTime, serialize(new SubscriptionProperties("7"))), // updated subscription state
                new DynamoDbRecord("6", "subscription_cache", "mock-first", 103L, compress("data-first-old".getBytes())),
                new DynamoDbRecord("7", "subscription_cache", "mock-first", 104L, compress("data-first".getBytes())),
                new DynamoDbRecord("8", "subscription", "11", 105L, serialize(new SubscriptionProperties("10"))), // old subscription state
                new DynamoDbRecord("9", "subscription", "13", subscriptionTime, serialize(new SubscriptionProperties("11"))), // updated subscription state
                new DynamoDbRecord("10", "subscription_cache", "mock-second", 107L, compress("data-second-old".getBytes())),
                new DynamoDbRecord("11", "subscription_cache", "mock-second", 108L, compress("data-second".getBytes()))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(
                        11L,
                        "image-url-first",
                        """
                        An update from <b>mock-first</b>!
                        
                        view-first
                        """.trim().stripIndent()
                ),
                new TelegramApiMock.SentMessage(
                        12L,
                        "image-url-first",
                        """
                        An update from <b>mock-first</b>!
                        
                        view-first
                        """.trim().stripIndent()
                ),
                new TelegramApiMock.SentMessage(
                        13L,
                        "image-url-second",
                        """
                        An update from <b>mock-second</b>!
                        
                        view-second
                        """.trim().stripIndent()
                )
        ), telegramApi.getSentMessages());
    }

    private byte[] serialize(Object value) {
        return compress(jsonMapper.writeValueAsBytes(value));
    }

}
