package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
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

public class TestJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(TestJobHandler.class);

    private final StorageService storageService;
    private final TelegramService telegramService;

    public TestJobHandler() {
        final JsonMapper jsonMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        final DynamoDbService dynamoDbService = new DynamoDbService(
                DynamoDbClient.create(),
                "event-notify-bot-table"
        );
        this.storageService = new StorageService(
                dynamoDbService,
                jsonMapper,
                20
        );

        final HttpService httpService = new HttpService(Duration.ofSeconds(1));
        final TelegramApi telegramApi = new TelegramApi(
                httpService,
                jsonMapper,
                System.getenv("TELEGRAM_BOT_TOKEN")
        );
        this.telegramService = new TelegramService(telegramApi);
    }

    @Override
    public void run(Duration extraTimeout) throws Exception {
        log.info("Initial settings: {}", storageService.getSettings());

        storageService.fetch();
        log.info("Settings after fetch: {}", storageService.getSettings());
        log.info("Flushing: {} WCU consumed (expecting 0)", storageService.flush());

        storageService.setSettings(new Settings(1L));
        log.info("Settings after set: {}", storageService.getSettings());
        log.info("Flushing: {} WCU consumed (expecting 1)", storageService.flush());

        storageService.setSettings(new Settings(2L));
        log.info("Settings after second set: {}", storageService.getSettings());
        storageService.setSettings(new Settings(1L));
        log.info("Settings after reverting: {}", storageService.getSettings());
        log.info("Flushing: {} WCU consumed (expecting 0)", storageService.flush());

        storageService.fetch();
        log.info("Settings after fetch: {}", storageService.getSettings());
        log.info("Flushing: {} WCU consumed (expecting 0)", storageService.flush());
    }

}
