package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import com.lonebytesoft.hamster.eventnotifybot.service.core.CommandParsingService;
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

public class ApplicationContext {

    private static final Logger log = LoggerFactory.getLogger(ApplicationContext.class);

    private static final String DYNAMODB_TABLE_NAME = "event-notify-bot-table";
    private static final int WRITE_COST_LIMIT = 20;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(1);

    private final StorageService storageService;
    private final TelegramService telegramService;
    private final CommandParsingService commandParsingService;

    public ApplicationContext() {
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

        this.commandParsingService = new CommandParsingService();
    }

    public void initialize() {
        log.debug("Initializing storage: fetch, read cost {}", storageService.fetch());
        log.debug("Initializing Telegram: me {}", telegramService.getMe());
    }

    public StorageService getStorageService() {
        return storageService;
    }

    public TelegramService getTelegramService() {
        return telegramService;
    }

    public CommandParsingService getCommandParsingService() {
        return commandParsingService;
    }

}
