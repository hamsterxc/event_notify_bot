package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.StorageUtils;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.DynamoDbServiceMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.ProviderMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.TelegramApiMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;

import static com.lonebytesoft.hamster.eventnotifybot.test.Assertions.assertDynamoDbRecordsEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscribeCommandTest {

    private final DynamoDbServiceMock dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final InstantSource instantSource = InstantSource.fixed(Instant.parse("2026-01-01T12:00:00Z"));
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper, instantSource, 5);
    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);
    private final Provider provider = new ProviderMock("mock", "data", Instant.parse("2026-01-01T18:00:00Z"), "image-url", "view");

    private final StorageUtils storageUtils = new StorageUtils(dynamoDbService, jsonMapper);

    private final ExecutableCommand command = new SubscribeCommand(1L, provider, instantSource);

    @BeforeEach
    public void beforeEach() {
        dynamoDbService.reset();
        telegramApi.reset();
    }

    @Test
    public void execute_noProviderStateExists_stateUpdated() {
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertFalse(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "provider_state", "mock", Instant.parse("2026-01-01T18:00:00Z").getEpochSecond(), serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void execute_anotherProviderStateExists_stateUpdated() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertFalse(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord(null, "provider_state", "mock", Instant.parse("2026-01-01T18:00:00Z").getEpochSecond(), serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void execute_staleProviderStateExists_stateUpdated() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "mock", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertFalse(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "provider_state", "mock", Instant.parse("2026-01-01T18:00:00Z").getEpochSecond(), serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void execute_almostStaleProviderStateExists_cacheAndSubscriptionCreated() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "mock", Instant.parse("2026-01-01T12:00:20Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        final Map.Entry<String, String> subscriptionCacheRef = storageUtils.getSubscriptionCacheRefs().entrySet().iterator().next();
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "mock", Instant.parse("2026-01-01T12:00:20Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord(subscriptionCacheRef.getValue(), "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord(subscriptionCacheRef.getKey(), "subscription", "1", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize(jsonMapper.writeValueAsString(new SubscriptionProperties(subscriptionCacheRef.getValue()))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Subscribed to updates from <b>mock</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_providerStateExists_cacheAndSubscriptionCreated() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        final Map.Entry<String, String> subscriptionCacheRef = storageUtils.getSubscriptionCacheRefs().entrySet().iterator().next();
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord(subscriptionCacheRef.getValue(), "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord(subscriptionCacheRef.getKey(), "subscription", "1", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize(jsonMapper.writeValueAsString(new SubscriptionProperties(subscriptionCacheRef.getValue()))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Subscribed to updates from <b>mock</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_providerStateCacheExist_subscriptionCreated() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-2", "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        final Map.Entry<String, String> subscriptionCacheRef = storageUtils.getSubscriptionCacheRefs().entrySet().iterator().next();
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord(subscriptionCacheRef.getValue(), "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord(subscriptionCacheRef.getKey(), "subscription", "1", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize(jsonMapper.writeValueAsString(new SubscriptionProperties(subscriptionCacheRef.getValue()))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Subscribed to updates from <b>mock</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_providerStateExistsAnotherCacheExists_cacheAndSubscriptionCreated() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-2", "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data-2")))
        ));
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        final Map.Entry<String, String> subscriptionCacheRef = storageUtils.getSubscriptionCacheRefs().entrySet().iterator().next();
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord("id-2", "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data-2")),
                new DynamoDbRecord(subscriptionCacheRef.getValue(), "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord(subscriptionCacheRef.getKey(), "subscription", "1", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize(jsonMapper.writeValueAsString(new SubscriptionProperties(subscriptionCacheRef.getValue()))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Subscribed to updates from <b>mock</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void execute_providerStateCacheSubscriptionExist_errorMessageSent() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-2", "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-3", "subscription", "1", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("id-2")))))
        ));
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord("id-2", "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data")),
                new DynamoDbRecord("id-3", "subscription", "1", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("id-2"))))
        ), dynamoDbService.read().records());
        assertEquals(List.of(new TelegramApiMock.SentMessage(
                1L,
                null,
                """
                Invalid command: Already subscribed to <b>mock</b>.
                """.trim().stripIndent()
        )), telegramApi.getSentMessages());
    }

    @Test
    public void estimateWriteCost_noProviderStateExists_providerUpdateUnknownCost() {
        storageService.fetch();

        assertNull(command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_anotherProviderStateExists_providerUpdateUnknownCost() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "foo", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        assertNull(command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_staleProviderStateExists_providerUpdateUnknownCost() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        assertNull(command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_almostStaleProviderStateExists_providerUpdateUnknownCost() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T12:00:20Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        assertNull(command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_providerStateExists_cacheAndSubscriptionCreateCost() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        assertEquals(2, command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_providerStateCacheExist_subscriptionCreateCost() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-2", "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        assertEquals(1, command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_providerStateExistsAnotherCacheExists_cacheAndSubscriptionCreateCost() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-2", "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data-2")))
        ));
        storageService.fetch();

        assertEquals(2, command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_providerStateCacheSubscriptionExist_noCost() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-2", "subscription_cache", "mock", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize("data"))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-3", "subscription", "1", Instant.parse("2026-01-01T11:00:00Z").getEpochSecond(), serialize(jsonMapper.writeValueAsString(new SubscriptionProperties("id-2")))))
        ));
        storageService.fetch();

        assertEquals(0, command.estimateWriteCost(storageService));
    }

    private byte[] serialize(final String value) {
        return ZipUtils.compress(value.getBytes());
    }

}
