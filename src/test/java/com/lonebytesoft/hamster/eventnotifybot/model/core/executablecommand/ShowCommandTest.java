package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.DynamoDbServiceMock;
import com.lonebytesoft.hamster.eventnotifybot.test.ProviderMock;
import com.lonebytesoft.hamster.eventnotifybot.test.TelegramApiMock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.lonebytesoft.hamster.eventnotifybot.test.Assertions.assertDynamoDbRecordsEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShowCommandTest {

    private final DynamoDbService dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper, 5);
    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);
    private final Provider provider = new ProviderMock("mock", "data", "image-url", "view");

    @Test
    public void execute_noProviderStateExists() {
        storageService.fetch();

        final ExecutableCommand command = new ShowCommand(1L, provider);
        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertFalse(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord(null, "provider_state", "mock", null, serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void execute_anotherProviderStateExists() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "foo", 2L, serialize("data")))
        ));
        storageService.fetch();

        final ExecutableCommand command = new ShowCommand(1L, provider);
        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertFalse(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "foo", 2L, serialize("data")),
                new DynamoDbRecord(null, "provider_state", "mock", null, serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(), telegramApi.getSentMessages());
    }

    @Test
    public void execute_providerStateExists() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("11", "provider_state", "mock", 2L, serialize("data")))
        ));
        storageService.fetch();

        final ExecutableCommand command = new ShowCommand(1L, provider);
        final boolean executionResult = command.execute(storageService, telegramService);
        storageService.flush();

        assertTrue(executionResult);
        assertDynamoDbRecordsEquals(List.of(
                new DynamoDbRecord("11", "provider_state", "mock", 2L, serialize("data"))
        ), dynamoDbService.read().records());
        assertEquals(List.of(
                new TelegramApiMock.SentMessage(1L, "image-url", "view")
        ), telegramApi.getSentMessages());
    }

    @Test
    public void estimateWriteCost_noProviderStateExists() {
        storageService.fetch();

        final ExecutableCommand command = new ShowCommand(1L, provider);

        assertNull(command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_anotherProviderStateExists() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "foo", 2L, serialize("data")))
        ));
        storageService.fetch();

        final ExecutableCommand command = new ShowCommand(1L, provider);

        assertNull(command.estimateWriteCost(storageService));
    }

    @Test
    public void estimateWriteCost_providerStateExists() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("id-1", "provider_state", "mock", 2L, serialize("data")))
        ));
        storageService.fetch();

        final ExecutableCommand command = new ShowCommand(1L, provider);

        assertEquals(0, command.estimateWriteCost(storageService));
    }

    private byte[] serialize(final String value) {
        return ZipUtils.compress(value.getBytes());
    }

}
