package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.DynamoDbServiceMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.TelegramApiMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;

import static com.lonebytesoft.hamster.eventnotifybot.test.Assertions.assertDynamoDbRecordsEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnsubscribeCommandTest {

    private final DynamoDbServiceMock dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final InstantSource instantSource = InstantSource.fixed(Instant.parse("2026-01-01T12:00:00Z"));
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper, instantSource, 20);
    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);

    @BeforeEach
    public void beforeEach() {
        dynamoDbService.reset();
        telegramApi.reset();
    }

    @Test
    public void execute_emptyParameterNoSubscriptions() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data")))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, null).execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Invalid command: No active subscriptions.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_emptyParameterOneSubscription() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12")))))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, null).execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Unsubscribed from <b>foo</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_emptyParameterTwoSubscriptions() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data-1"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data-1"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("14", "provider_state", "bar", 104L, serialize("data-2"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("15", "subscription_cache", "bar", 105L, serialize("data-2"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("16", "subscription", "1", 106L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("15")))))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, null).execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data-1")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data-1")),
                new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12")))),
                new DynamoDbRecord("14", "provider_state", "bar", 104L, serialize("data-2")),
                new DynamoDbRecord("15", "subscription_cache", "bar", 105L, serialize("data-2")),
                new DynamoDbRecord("16", "subscription", "1", 106L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("15"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Invalid command: Multiple active subscriptions, must specify provider name.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_emptyParameterTwoSubscriptionsDifferentChats() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data-1"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data-1"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("14", "provider_state", "bar", 104L, serialize("data-2"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("15", "subscription_cache", "bar", 105L, serialize("data-2"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("16", "subscription", "2", 106L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("15")))))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, null).execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data-1")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data-1")),
                new DynamoDbRecord("14", "provider_state", "bar", 104L, serialize("data-2")),
                new DynamoDbRecord("15", "subscription_cache", "bar", 105L, serialize("data-2")),
                new DynamoDbRecord("16", "subscription", "2", 106L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("15"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Unsubscribed from <b>foo</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_withParameterNoSubscriptions() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data")))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, "foo").execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Invalid command: No active subscription to <b>foo</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_withParameterOneSubscription() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12")))))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, "foo").execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Unsubscribed from <b>foo</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_withParameterOneSubscriptionNotRequested() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12")))))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, "bar").execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data")),
                new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Invalid command: No active subscription to <b>bar</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_withParameterTwoSubscriptions() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data-1"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data-1"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("14", "provider_state", "bar", 104L, serialize("data-2"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("15", "subscription_cache", "bar", 105L, serialize("data-2"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("16", "subscription", "1", 106L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("15")))))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, "foo").execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data-1")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data-1")),
                new DynamoDbRecord("14", "provider_state", "bar", 104L, serialize("data-2")),
                new DynamoDbRecord("15", "subscription_cache", "bar", 105L, serialize("data-2")),
                new DynamoDbRecord("16", "subscription", "1", 106L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("15"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Unsubscribed from <b>foo</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_withParameterTwoSubscriptionsDifferentChats() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data-1"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data-1"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("13", "subscription", "1", 103L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("12"))))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("14", "provider_state", "bar", 104L, serialize("data-2"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("15", "subscription_cache", "bar", 105L, serialize("data-2"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("16", "subscription", "2", 106L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("15")))))
        ));
        storageService.fetch();

        final boolean executionResult = new UnsubscribeCommand(1L, "foo").execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 101L, serialize("data-1")),
                new DynamoDbRecord("12", "subscription_cache", "foo", 102L, serialize("data-1")),
                new DynamoDbRecord("14", "provider_state", "bar", 104L, serialize("data-2")),
                new DynamoDbRecord("15", "subscription_cache", "bar", 105L, serialize("data-2")),
                new DynamoDbRecord("16", "subscription", "2", 106L, serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("15"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Unsubscribed from <b>foo</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void estimateWriteCost() {
        final ExecutableCommand command = new UnsubscribeCommand(1L, "mock");

        assertEquals(1, command.estimateWriteCost(null));
    }

    private byte[] serialize(final String value) {
        return ZipUtils.compress(value.getBytes());
    }

}
