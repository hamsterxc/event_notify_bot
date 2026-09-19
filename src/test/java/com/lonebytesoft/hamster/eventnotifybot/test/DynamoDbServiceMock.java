package com.lonebytesoft.hamster.eventnotifybot.test;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DynamoDbServiceMock extends DynamoDbService {

    private final Map<String, DynamoDbRecord> storage = new HashMap<>();

    public DynamoDbServiceMock() {
        super(null, null);
    }

    @Override
    public DynamoDbReadResponse read() {
        return new DynamoDbReadResponse(
                storage.values(),
                storage.size()
        );
    }

    @Override
    public int write(Collection<DynamoDbWriteRequest> writeRequests) {
        writeRequests
                .stream()
                .map(DynamoDbWriteRequest::toDynamoDbRequest)
                .forEach(writeRequest -> Optional.ofNullable(writeRequest.putRequest())
                        .map(PutRequest::item)
                        .map(DynamoDbRecord::new)
                        .ifPresentOrElse(
                                record -> storage.put(record.id(), record),
                                () -> Optional.ofNullable(writeRequest.deleteRequest())
                                        .map(DeleteRequest::key)
                                        .map(key -> key.get("id"))
                                        .map(AttributeValue::s)
                                        .ifPresentOrElse(
                                                storage::remove,
                                                () -> {
                                                    throw new IllegalArgumentException("Unprocessable write request: " + writeRequest);
                                                }
                                        )
                        ));
        return writeRequests.size();
    }

}
