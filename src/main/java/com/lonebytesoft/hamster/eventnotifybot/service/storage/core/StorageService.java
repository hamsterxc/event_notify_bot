package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final int WCU_LIMIT = 25;
    private static final int WCU_WARN_LIMIT_PERCENTAGE = 80;
    private static final int WCU_WARN_LIMIT = (int) Math.ceil((double) WCU_LIMIT * WCU_WARN_LIMIT_PERCENTAGE / 100);

    private final DynamoDbService dynamoDbService;
    private final JsonMapper jsonMapper;

    private SettingsShadow settings = new SettingsShadow(List.of(), null);
    private CommandsShadow commands = new CommandsShadow(List.of(), null);
    private UnknownShadow unknown = new UnknownShadow(List.of());

    public StorageService(
            final DynamoDbService dynamoDbService,
            final JsonMapper jsonMapper
    ) {
        this.dynamoDbService = dynamoDbService;
        this.jsonMapper = jsonMapper;
    }

    public int fetch() {
        final DynamoDbReadResponse dynamoDbReadResponse = dynamoDbService.read();

        final Map<RecordType, List<DynamoDbRecord>> records = dynamoDbReadResponse.records()
                .stream()
                .collect(Collectors.groupingBy(record -> RecordType.fromValue(record.type())));

        this.settings = new SettingsShadow(records.getOrDefault(RecordType.SETTINGS, List.of()), jsonMapper);
        this.commands = new CommandsShadow(records.getOrDefault(RecordType.COMMAND, List.of()), jsonMapper);
        this.unknown = new UnknownShadow(records.getOrDefault(RecordType.UNKNOWN, List.of()));

        return dynamoDbReadResponse.consumedCapacity();
    }

    public void cleanup() {
        settings.cleanup();
        unknown.cleanup();
    }

    public int flush() {
        final Collection<DynamoDbWriteRequest> writeRequests = Stream.of(settings, commands, unknown)
                .map(StorageShadow::flush)
                .flatMap(Collection::stream)
                .toList();

        final int wcuConsumed = writeRequests.isEmpty()
                ? 0
                : dynamoDbService.write(writeRequests);
        if (wcuConsumed < WCU_WARN_LIMIT) {
            log.debug("{} WCU consumed while flushing", wcuConsumed);
        } else {
            log.warn("Consumed more than {}% of limit while flushing: {} WCU consumed", WCU_WARN_LIMIT_PERCENTAGE, wcuConsumed);
        }

        return wcuConsumed;
    }

    public Settings getSettings() {
        return this.settings.get();
    }

    public void setSettings(final Settings settings) {
        this.settings.set(System.currentTimeMillis(), settings);
    }

    public Collection<Command> getCommands() {
        return this.commands.getAll();
    }

    public void addCommand(
            final Long chatId,
            final Long time,
            final String command,
            final List<String> parameters
    ) {
        this.commands.add(new Command(
                UUID.randomUUID().toString(),
                chatId,
                time,
                command,
                parameters
        ));
    }

    public void removeCommand(final String id) {
        this.commands.remove(id);
    }

}
