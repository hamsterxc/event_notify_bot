package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.DynamoDbServiceMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.ProviderMock;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.TelegramApiMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;

import static com.lonebytesoft.hamster.eventnotifybot.test.Assertions.assertDynamoDbRecordsEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProviderUpdateCommandTest {

    private final DynamoDbServiceMock dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final InstantSource instantSource = InstantSource.fixed(Instant.parse("2026-01-01T12:00:00Z"));
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper, instantSource, 5);
    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);
    private final Provider provider = new ProviderMock("mock", "data", Instant.parse("2026-01-01T18:00:00Z"), "image-url", "view");

    private final ExecutableCommand command = new ProviderUpdateCommand(provider);

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

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "provider_state", "mock", Instant.parse("2026-01-01T18:00:00Z").getEpochSecond(), serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void execute_noProviderStateExistsEmptyData_nothingUpdated() {
        storageService.fetch();

        final Provider provider = new ProviderMock("mock", null, Instant.parse("2026-01-01T18:00:00Z"), "image-url", "view");
        final ExecutableCommand command = new ProviderUpdateCommand(provider);
        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(), dynamoDbService.read().records());
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

        assertTrue(executionResult);
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

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "provider_state", "mock", Instant.parse("2026-01-01T18:00:00Z").getEpochSecond(), serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void execute_providerStateExists_stateUpdated() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "mock", Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(), serialize("data")))
        ));
        storageService.fetch();

        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "mock", Instant.parse("2026-01-01T18:00:00Z").getEpochSecond(), serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void estimateWriteCost() {
        storageService.fetch();

        assertNull(command.estimateWriteCost(null));
    }

    private byte[] serialize(final String value) {
        return ZipUtils.compress(value.getBytes());
    }

}
