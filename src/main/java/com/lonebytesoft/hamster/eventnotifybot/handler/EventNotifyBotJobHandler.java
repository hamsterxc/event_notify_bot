package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramApi;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

public class EventNotifyBotJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(EventNotifyBotJobHandler.class);

    private static final String DYNAMODB_TABLE_NAME = "event-notify-bot-table";
    private static final int WRITE_COST_LIMIT = 20;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(1);

    private final StorageService storageService;
    private final TelegramService telegramService;

    public EventNotifyBotJobHandler() {
        final JsonMapper jsonMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        final DynamoDbService dynamoDbService = new DynamoDbService(
                DynamoDbClient.create(),
                DYNAMODB_TABLE_NAME
        );
        this.storageService = new StorageService(
                dynamoDbService,
                jsonMapper,
                WRITE_COST_LIMIT
        );

        final HttpService httpService = new HttpService(HTTP_TIMEOUT);
        final TelegramApi telegramApi = new TelegramApi(
                httpService,
                jsonMapper,
                System.getenv("TELEGRAM_BOT_TOKEN")
        );
        this.telegramService = new TelegramService(telegramApi);
    }

    @Override
    public void run(Duration extraTimeout) throws Exception {
        // todo: implement job handling
        log.info("Job run");
    }

}
