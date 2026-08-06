package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Chat;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramApi;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

public class TestJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(TestJobHandler.class);

    private final String tableName;
    private final DynamoDbClient dynamoDbClient;
    private final JsonMapper jsonMapper;
    private final TelegramService telegramService;

    public TestJobHandler() {
        this.tableName = "event-notify-bot-table";
        this.dynamoDbClient = DynamoDbClient.create();

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
        final ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .consistentRead(true)
                .build();
        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
        final List<Map<String, AttributeValue>> scanItems = new ArrayList<>(scanResponse.items());
        final List<ConsumedCapacity> scanConsumedCapacities = new ArrayList<>();
        scanConsumedCapacities.add(scanResponse.consumedCapacity());
        while (scanResponse.hasLastEvaluatedKey() && !scanResponse.lastEvaluatedKey().isEmpty()) {
            scanResponse = dynamoDbClient.scan(scanRequest.toBuilder().exclusiveStartKey(scanResponse.lastEvaluatedKey()).build());
            scanItems.addAll(scanResponse.items());
            scanConsumedCapacities.add(scanResponse.consumedCapacity());
        }
        log.info("{}", scanItems);
        scanItems.forEach(item -> {
            try {
                log.info("id={}, type={}, subject={}, time={}, data={}",
                        item.get("id").s(),
                        item.get("type").s(),
                        item.get("subject").s(),
                        DateTimeFormatter.ISO_DATE_TIME.format(Instant.ofEpochMilli(Long.parseLong(item.get("time").n())).atZone(ZoneId.systemDefault())),
                        jsonMapper.readValue(ZipUtils.decompress(item.get("data").b().asByteArray()), Map.class));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        log.info("Scan consumed capacity: {}", scanConsumedCapacities);
        log.info("Total scan consumed capacity: read {}, write {}, total {}",
                scanConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.readCapacityUnits()).orElse(0.0)).sum(),
                scanConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.writeCapacityUnits()).orElse(0.0)).sum(),
                scanConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.capacityUnits()).orElse(0.0)).sum());

        // testing bulk write
        final List<WriteRequest> writeRequests = IntStream.range(scanItems.size(), 5)
                .mapToObj(i -> WriteRequest.builder().putRequest(putRequest -> {
                    try {
                        putRequest.item(Map.of(
                                "id", AttributeValue.builder().s(String.valueOf(i)).build(),
                                "type", AttributeValue.builder().s("test").build(),
                                "subject", AttributeValue.builder().s(String.valueOf(i + 1000)).build(),
                                "time", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build(),
                                "data", AttributeValue.builder().b(SdkBytes.fromByteArray(ZipUtils.compress(jsonMapper.writeValueAsBytes(Map.of("attribute", i))))).build()
                        )).build();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).build())
                .toList();
        if (!writeRequests.isEmpty()) {
            BatchWriteItemResponse batchPutItemResponse = dynamoDbClient.batchWriteItem(batchWriteItemRequest -> batchWriteItemRequest
                    .requestItems(Map.of(tableName, writeRequests))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL));
            final List<ConsumedCapacity> writeConsumedCapacities = new ArrayList<>(batchPutItemResponse.consumedCapacity());
            int attempts = 1;
            while ((attempts <= 3) && batchPutItemResponse.hasUnprocessedItems()
                    && Optional.ofNullable(batchPutItemResponse.unprocessedItems())
                    .map(items -> items.get(tableName))
                    .filter(items -> !items.isEmpty())
                    .isPresent()) {
                final Map<String, List<WriteRequest>> unprocessedItems = batchPutItemResponse.unprocessedItems();
                log.warn("Failed to process {} put requests, retrying...", unprocessedItems.get(tableName).size());
                try {
                    Thread.sleep(Duration.ofSeconds(1));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                attempts++;
                batchPutItemResponse = dynamoDbClient.batchWriteItem(batchWriteItemRequest -> batchWriteItemRequest
                        .requestItems(unprocessedItems)
                        .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL));
                writeConsumedCapacities.addAll(batchPutItemResponse.consumedCapacity());
            }
            log.info("Batch write consumed capacity: {}", writeConsumedCapacities);
            log.info("Total batch write consumed capacity: read {}, write {}, total {}",
                    writeConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.readCapacityUnits()).orElse(0.0)).sum(),
                    writeConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.writeCapacityUnits()).orElse(0.0)).sum(),
                    writeConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.capacityUnits()).orElse(0.0)).sum());
        }

        // testing bulk delete
        final List<WriteRequest> deleteRequests = IntStream.range(2, 5)
                .mapToObj(i -> WriteRequest.builder().deleteRequest(deleteRequest -> deleteRequest.key(Map.of("id", AttributeValue.builder().s(String.valueOf(i)).build()))).build())
                .toList();
        BatchWriteItemResponse batchDeleteItemResponse = dynamoDbClient.batchWriteItem(batchWriteItemRequest -> batchWriteItemRequest
                .requestItems(Map.of(tableName, deleteRequests))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL));
        final List<ConsumedCapacity> deleteConsumedCapacities = new ArrayList<>(batchDeleteItemResponse.consumedCapacity());
        int attempts = 1;
        while ((attempts <= 3) && batchDeleteItemResponse.hasUnprocessedItems()
                && Optional.ofNullable(batchDeleteItemResponse.unprocessedItems())
                .map(items -> items.get(tableName))
                .filter(items -> !items.isEmpty())
                .isPresent()) {
            final Map<String, List<WriteRequest>> unprocessedItems = batchDeleteItemResponse.unprocessedItems();
            log.warn("Failed to process {} delete requests, retrying...", unprocessedItems.get(tableName).size());
            try {
                Thread.sleep(Duration.ofSeconds(1));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            attempts++;
            batchDeleteItemResponse = dynamoDbClient.batchWriteItem(batchWriteItemRequest -> batchWriteItemRequest
                    .requestItems(unprocessedItems)
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL));
            deleteConsumedCapacities.addAll(batchDeleteItemResponse.consumedCapacity());
        }
        log.info("Batch delete consumed capacity: {}", deleteConsumedCapacities);
        log.info("Total batch delete consumed capacity: read {}, write {}, total {}",
                deleteConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.readCapacityUnits()).orElse(0.0)).sum(),
                deleteConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.writeCapacityUnits()).orElse(0.0)).sum(),
                deleteConsumedCapacities.stream().mapToDouble(consumedCapacity -> Optional.ofNullable(consumedCapacity.capacityUnits()).orElse(0.0)).sum());

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
