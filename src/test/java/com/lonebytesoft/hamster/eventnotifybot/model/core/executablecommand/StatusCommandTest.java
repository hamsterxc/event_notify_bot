package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.DynamoDbServiceMock;
import com.lonebytesoft.hamster.eventnotifybot.test.TelegramApiMock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StatusCommandTest {

    private final DynamoDbService dynamoDbService = new DynamoDbServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final StorageService storageService = new StorageService(dynamoDbService, jsonMapper, 0);
    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);

    @Test
    public void execute_noSubscriptions() {
        storageService.fetch();

        final ExecutableCommand command = new StatusCommand(1L);
        command.execute(storageService, telegramService);

        assertEquals(
                List.of(new TelegramApiMock.SentMessage(
                        1L,
                        null,
                        """
                        Current subscriptions:
                        - <i>(none yet)</i>
                        """.trim().stripIndent()
                )),
                telegramApi.getSentMessages()
        );
    }

    @Test
    public void execute_noSubscriptionsOfCurrentChat() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "subscription_cache", "babylon", 11L, serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "subscription", "2", 12L, serialize(new SubscriptionProperties("1"))))
        ));
        storageService.fetch();

        final ExecutableCommand command = new StatusCommand(1L);
        command.execute(storageService, telegramService);

        assertEquals(
                List.of(new TelegramApiMock.SentMessage(
                        1L,
                        null,
                        """
                        Current subscriptions:
                        - <i>(none yet)</i>
                        """.trim().stripIndent()
                )),
                telegramApi.getSentMessages()
        );
    }

    @Test
    public void execute_someSubscriptions() {
        dynamoDbService.write(List.of(
                DynamoDbWriteRequest.put(new DynamoDbRecord("1", "subscription_cache", "runcity", 11L, serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("2", "subscription", "1", 12L, serialize(new SubscriptionProperties("1")))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("3", "subscription_cache", "babylon", 13L, serialize(List.of()))),
                DynamoDbWriteRequest.put(new DynamoDbRecord("4", "subscription", "1", 14L, serialize(new SubscriptionProperties("3"))))
        ));
        storageService.fetch();

        final ExecutableCommand command = new StatusCommand(1L);
        command.execute(storageService, telegramService);

        assertEquals(
                List.of(new TelegramApiMock.SentMessage(
                        1L,
                        null,
                        """
                        Current subscriptions:
                        - <b>babylon</b>
                        - <b>runcity</b>
                        """.trim().stripIndent()
                )),
                telegramApi.getSentMessages()
        );
    }

    @Test
    public void estimateWriteCost() {
        final ExecutableCommand command = new StatusCommand(1L);

        assertEquals(0, command.estimateWriteCost(null));
    }

    private byte[] serialize(Object value) {
        return ZipUtils.compress(jsonMapper.writeValueAsBytes(value));
    }

}
