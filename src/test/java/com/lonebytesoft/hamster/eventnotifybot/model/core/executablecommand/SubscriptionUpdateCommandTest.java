package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.ProviderView;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.StorageUtils;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.DynamoDbServiceMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.TelegramApiMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;

import static com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils.compress;
import static com.lonebytesoft.hamster.eventnotifybot.test.Assertions.assertDynamoDbRecordsEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubscriptionUpdateCommandTest {

    private final DynamoDbServiceMock dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final InstantSource instantSource = InstantSource.fixed(Instant.parse("2026-01-01T12:00:00Z"));
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper, instantSource, 20);
    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);
    private final Provider provider = new ProviderMock("mock");

    private final StorageUtils storageUtils = new StorageUtils(dynamoDbService, jsonMapper);

    @BeforeEach
    public void beforeEach() {
        dynamoDbService.reset();
        telegramApi.reset();
    }

    @Test
    public void execute_noSubscription() {
        stateNoSubscription();

        final ExecutableCommand command = new SubscriptionUpdateCommand(11L, provider, "old", "new");
        assertTrue(command.execute(storageService, telegramService));
        assertEquals(0, storageService.flush()); // nothing is written

        assertDynamoDbRecordsEquals(List.of(), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void estimateWriteCost_noSubscription() {
        stateNoSubscription();

        final ExecutableCommand command = new SubscriptionUpdateCommand(11L, provider, "old", "new");
        assertEquals(2, command.estimateWriteCost(storageService)); // 1 for cache + 1 for subscription itself, even though nothing will really be written
    }

    private void stateNoSubscription() {
        storageService.fetch();
    }

    @Test
    public void execute_subscriptionAnotherChat() {
        stateSubscriptionAnotherChat();

        final ExecutableCommand command = new SubscriptionUpdateCommand(11L, provider, "old", "new");
        assertTrue(command.execute(storageService, telegramService));
        assertEquals(0, storageService.flush()); // nothing is written

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "subscription_cache", "mock", 101L, compress("old".getBytes())),
                new DynamoDbRecord("2", "subscription", "12", 102L, serialize(new SubscriptionProperties("1")))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void estimateWriteCost_subscriptionAnotherChat() {
        stateSubscriptionAnotherChat();

        final ExecutableCommand command = new SubscriptionUpdateCommand(11L, provider, "old", "new");
        assertEquals(2, command.estimateWriteCost(storageService)); // 1 for cache + 1 for subscription itself, even though nothing will really be written
    }

    private void stateSubscriptionAnotherChat() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "subscription_cache", "mock", 101L, compress("old".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "subscription", "12", 102L, serialize(new SubscriptionProperties("1"))))
        ));
        storageService.fetch();
    }

    @Test
    public void execute_noNewDataCacheEntry() {
        stateSubscriptionNoNewDataCacheEntry();

        final ExecutableCommand command = new SubscriptionUpdateCommand(11L, provider, "old", "new");
        assertTrue(command.execute(storageService, telegramService));
        assertEquals(2, storageService.flush()); // 1 for cache + 1 for subscription itself

        final long time = Instant.parse("2026-01-01T12:00:00Z").getEpochSecond();
        final String cacheRef = storageUtils.getSubscriptionCacheRefs().get("2");
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "subscription_cache", "mock", 101L, compress("old".getBytes())),
                new DynamoDbRecord(cacheRef, "subscription_cache", "mock", time, compress("new".getBytes())),
                new DynamoDbRecord("2", "subscription", "11", time, serialize(new SubscriptionProperties(cacheRef)))
        ), dynamoDbService.read().records());
        assertEquals(
                List.of(new TelegramApiMock.SentMessage(
                        11L,
                        "image-url",
                        """
                        An update from <b>mock</b>!
                        
                        old -> new
                        """.trim().stripIndent()
                )),
                telegramApi.getSentMessages()
        );
    }

    @Test
    public void estimateWriteCost_noNewDataCacheEntry() {
        stateSubscriptionNoNewDataCacheEntry();

        final ExecutableCommand command = new SubscriptionUpdateCommand(11L, provider, "old", "new");
        assertEquals(2, command.estimateWriteCost(storageService)); // 1 for cache + 1 for subscription itself
    }

    private void stateSubscriptionNoNewDataCacheEntry() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "subscription_cache", "mock", 101L, compress("old".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "subscription", "11", 102L, serialize(new SubscriptionProperties("1"))))
        ));
        storageService.fetch();
    }

    @Test
    public void execute_existingNewDataCacheEntry() {
        stateSubscriptionExistingNewDataCacheEntry();

        final ExecutableCommand command = new SubscriptionUpdateCommand(11L, provider, "old", "new");
        assertTrue(command.execute(storageService, telegramService));
        assertEquals(1, storageService.flush()); // 1 for subscription itself

        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("1", "subscription_cache", "mock", 101L, compress("old".getBytes())),
                new DynamoDbRecord("2", "subscription_cache", "mock", 102L, compress("new".getBytes())),
                new DynamoDbRecord("3", "subscription", "11", Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(), serialize(new SubscriptionProperties("2")))
        ), dynamoDbService.read().records());
        assertEquals(
                List.of(new TelegramApiMock.SentMessage(
                        11L,
                        "image-url",
                        """
                        An update from <b>mock</b>!
                        
                        old -> new
                        """.trim().stripIndent()
                )),
                telegramApi.getSentMessages()
        );
    }

    @Test
    public void estimateWriteCost_existingNewDataCacheEntry() {
        stateSubscriptionExistingNewDataCacheEntry();

        final ExecutableCommand command = new SubscriptionUpdateCommand(11L, provider, "old", "new");
        assertEquals(1, command.estimateWriteCost(storageService)); // 1 for subscription itself
    }

    private void stateSubscriptionExistingNewDataCacheEntry() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "subscription_cache", "mock", 101L, compress("old".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "subscription_cache", "mock", 102L, compress("new".getBytes()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "subscription", "11", 103L, serialize(new SubscriptionProperties("1"))))
        ));
        storageService.fetch();
    }

    private byte[] serialize(Object value) {
        return compress(jsonMapper.writeValueAsBytes(value));
    }

    private record ProviderMock(
            String name
    ) implements Provider {

        @Override
        public String name() {
            return name;
        }

        @Override
        public Optional<String> getData() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant getNextUpdateTime() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProviderView> getStateView(String data, Integer limit, Integer limitWithImage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProviderView> getDiffView(String baseData, String newData, Integer limit, Integer limitWithImage) {
            return Optional.of(new ProviderView(
                    "image-url",
                    "%s -> %s".formatted(baseData, newData)
            ));
        }

    }

}
