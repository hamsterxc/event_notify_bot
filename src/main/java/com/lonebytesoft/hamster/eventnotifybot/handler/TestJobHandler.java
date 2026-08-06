package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Chat;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.DynamoDbService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramApi;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

public class TestJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(TestJobHandler.class);

    private final DynamoDbService dynamoDbService;
    private final JsonMapper jsonMapper;
    private final TelegramService telegramService;

    public TestJobHandler() {
        this.dynamoDbService = new DynamoDbService(
                DynamoDbClient.create(),
                "event-notify-bot-table"
        );

        this.jsonMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

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
        // testing full scan
        final DynamoDbReadResponse readResponse = dynamoDbService.read();
        log.info("{}", readResponse);
        readResponse.records().forEach(record -> {
            try {
                log.info("id={}, type={}, subject={}, time={}, data={}",
                        record.id(),
                        record.type(),
                        record.subject(),
                        DateTimeFormatter.ISO_DATE_TIME.format(Instant.ofEpochMilli(record.time()).atZone(ZoneId.systemDefault())),
                        jsonMapper.readValue(ZipUtils.decompress(record.data()), Map.class));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        log.info("Scan consumed capacity: {}", readResponse.consumedCapacity());

        // testing bulk write
        final List<DynamoDbWriteRequest> writeRequests = IntStream.range(0, 5)
                .mapToObj(i -> {
                    try {
                        return DynamoDbWriteRequest.put(new DynamoDbRecord(
                                String.valueOf(i),
                                "test",
                                String.valueOf(i + 1000),
                                System.currentTimeMillis(),
                                ZipUtils.compress(jsonMapper.writeValueAsBytes(Map.of("attribute", i)))
                        ));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        final int writeConsumedCapacity = dynamoDbService.write(writeRequests);
        log.info("Write consumed capacity: {}", writeConsumedCapacity);

        // testing bulk delete
        final List<DynamoDbWriteRequest> deleteRequests = IntStream.range(2, 5)
                .mapToObj(i -> DynamoDbWriteRequest.delete(String.valueOf(i)))
                .toList();
        final int deleteConsumedCapacity = dynamoDbService.write(deleteRequests);
        log.info("Delete consumed capacity: {}", deleteConsumedCapacity);

        // testing HTTP calls (Telegram API)
        telegramService.getUpdates(null)
                .stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(Update::id))
                .ifPresent(update -> {
                    final Long chatId = Optional.ofNullable(update.message()).map(Message::chat).map(Chat::id).orElse(null);
                    log.info("Sending message to chat {}", chatId);
                    telegramService.sendMessage(
                            chatId,
                            "https://babylonberlin.eu/images/regridart/500x350/images/stummfilme/metropoli_gold_web500.jpg",
                            Optional.ofNullable(update.message()).map(Message::text).map(text -> text  + " back at you!").orElse(null),
                            false
                    );
                });

        // testing logging
        log.trace("Test log message: trace");
        log.debug("Test log message: debug");
        log.info("Test log message: info");
        log.warn("Test log message: warn");
        log.error("Test log message: error");
    }

}
