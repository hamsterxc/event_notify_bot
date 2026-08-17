package com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DynamoDbService {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbService.class);

    private static final int WRITE_RETRY_ATTEMPTS = 3;
    private static final Duration WRITE_RETRY_INTERVAL = Duration.ofSeconds(1);

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbService(
            final DynamoDbClient dynamoDbClient,
            final String tableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public DynamoDbReadResponse read() {
        final ScanRequest request = ScanRequest.builder()
                .tableName(tableName)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .consistentRead(true)
                .build();

        ScanResponse response = dynamoDbClient.scan(request);
        final Collection<Map<String, AttributeValue>> items = new ArrayList<>(response.items());
        final Collection<ConsumedCapacity> consumedCapacities = new ArrayList<>();
        consumedCapacities.add(response.consumedCapacity());

        while (response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()) {
            response = dynamoDbClient.scan(request.toBuilder().exclusiveStartKey(response.lastEvaluatedKey()).build());
            items.addAll(response.items());
            consumedCapacities.add(response.consumedCapacity());
        }

        final int consumedCapacity = (int) Math.ceil(consumedCapacities.stream()
                .mapToDouble(ConsumedCapacity::capacityUnits)
                .sum());
        log.debug("Read {} items from DynamoDB, consumed {} RCU", items.size(), consumedCapacity);
        return new DynamoDbReadResponse(
                items.stream()
                        .map(DynamoDbRecord::new)
                        .toList(),
                consumedCapacity
        );
    }

    public int write(final Collection<DynamoDbWriteRequest> writeRequests) {
        log.debug("Executing {} write requests against DynamoDB", writeRequests.size());
        final List<WriteRequest> requests = writeRequests
                .stream()
                .map(DynamoDbWriteRequest::toDynamoDbRequest)
                .toList();
        BatchWriteItemResponse response = dynamoDbClient.batchWriteItem(batchWriteItemRequest -> batchWriteItemRequest
                .requestItems(Map.of(tableName, requests))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL));
        final List<ConsumedCapacity> consumedCapacities = new ArrayList<>(response.consumedCapacity());

        int attempts = 1;
        while ((attempts <= WRITE_RETRY_ATTEMPTS) && hasUnprocessedWriteRequests(response)) {
            final Map<String, List<WriteRequest>> unprocessedItems = response.unprocessedItems();
            log.warn("Failed to process {} DynamoDB write requests, retrying...", unprocessedItems.get(tableName).size());
            try {
                Thread.sleep(WRITE_RETRY_INTERVAL);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            attempts++;
            response = dynamoDbClient.batchWriteItem(batchWriteItemRequest -> batchWriteItemRequest
                    .requestItems(unprocessedItems)
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL));
            consumedCapacities.addAll(response.consumedCapacity());
        }
        if (attempts > WRITE_RETRY_ATTEMPTS) {
            log.error("Could not process all DynamoDB write requests after {} attempts", WRITE_RETRY_ATTEMPTS);
            throw new IllegalStateException("Could not process all DynamoDB write requests");
        }

        final int consumedCapacity = (int) Math.ceil(consumedCapacities.stream()
                .mapToDouble(ConsumedCapacity::capacityUnits)
                .sum());
        log.debug("Executed {} write requests against DynamoDB, consumed {} WCU", writeRequests.size(), consumedCapacity);
        return consumedCapacity;
    }

    private boolean hasUnprocessedWriteRequests(final BatchWriteItemResponse response) {
        return response.hasUnprocessedItems()
                && Optional.ofNullable(response.unprocessedItems())
                .map(items -> items.get(tableName))
                .filter(items -> !items.isEmpty())
                .isPresent();
    }

}
