package com.lonebytesoft.hamster.eventnotifybot.service.storage;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.CommandProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.Settings;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final int WCU_LIMIT = 25;
    private static final int WCU_WARN_LIMIT_PERCENTAGE = 80;
    private static final int WCU_WARN_LIMIT = (int) Math.ceil((double) WCU_LIMIT * WCU_WARN_LIMIT_PERCENTAGE / 100);

    private final DynamoDbService dynamoDbService;
    private final JsonMapper jsonMapper;

    private SettingsShadow settings = new SettingsShadow((Settings) null);
    private CommandsShadow commands = new CommandsShadow(List.of());
    private final Collection<DynamoDbWriteRequest> writeRequests = new ArrayList<>();

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

        final Collection<DynamoDbRecord> settings = records.getOrDefault(RecordType.SETTINGS, List.of());
        this.settings = switch (settings.size()) {
            case 0:
                log.info("No settings fetched, falling back to default");
                yield defaultSettings();
            case 1:
                final DynamoDbRecord record = settings.iterator().next();
                log.debug("Fetched settings: {}", record);
                yield new SettingsShadow(record);
            default:
                log.warn("Multiple settings records fetched, leaving the latest and removing all others: {}", settings);
                final List<DynamoDbRecord> sortedSettings = settings
                        .stream()
                        .sorted(Comparator.comparing(DynamoDbRecord::time))
                        .toList();
                this.writeRequests.addAll(sortedSettings
                        .stream()
                        .skip(1)
                        .map(DynamoDbRecord::id)
                        .map(DynamoDbWriteRequest::delete)
                        .toList());
                yield new SettingsShadow(sortedSettings.getFirst());
        };

        final Collection<DynamoDbRecord> commands = records.getOrDefault(RecordType.COMMAND, List.of());
        log.debug("Fetched commands: {}", commands);
        this.commands = new CommandsShadow(commands);

        return dynamoDbReadResponse.consumedCapacity();
    }

    public int flush() {
        Optional.ofNullable(settings.flush())
                .filter(writeRequests -> !writeRequests.isEmpty())
                .ifPresent(writeRequests -> {
                    log.debug("Updating settings to {}", settings.getValue());
                    this.writeRequests.addAll(writeRequests);
                });

        Optional.ofNullable(commands.flush())
                .filter(writeRequests -> !writeRequests.isEmpty())
                .stream()
                .flatMap(Collection::stream)
                .forEach(writeRequest -> {
                    log.debug("Updating command: {}", writeRequest);
                    this.writeRequests.add(writeRequest);
                });

        final int wcuConsumed = writeRequests.isEmpty()
                ? 0
                : dynamoDbService.write(writeRequests);
        if (wcuConsumed < WCU_WARN_LIMIT) {
            log.debug("{} WCU consumed while flushing", wcuConsumed);
        } else {
            log.warn("Consumed more than {}% of limit while flushing: {} WCU consumed", WCU_WARN_LIMIT_PERCENTAGE, wcuConsumed);
        }

        writeRequests.clear();
        return wcuConsumed;
    }

    public Settings getSettings() {
        return this.settings.getValue();
    }

    public void setSettings(final Settings settings) {
        this.settings.setValue(settings);
    }

    private SettingsShadow defaultSettings() {
        return new SettingsShadow(new Settings(null));
    }

    public Collection<Command> getCommands() {
        return this.commands.getValue();
    }

    public void addCommand(
            final Long chatId,
            final Long time,
            final String command,
            final List<String> parameters
    ) {
        this.commands.addCommand(chatId, time, command, parameters);
    }

    public void removeCommand(final String id) {
        this.commands.removeCommand(id);
    }

    private enum RecordType {

        SETTINGS,
        COMMAND,
        UNKNOWN
        ;

        private static final Map<String, RecordType> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(RecordType::getValue, Function.identity()));

        public static RecordType fromValue(final String value) {
            return Optional.ofNullable(value)
                    .map(String::toLowerCase)
                    .map(BY_VALUE::get)
                    .orElse(RecordType.UNKNOWN);
        }

        public String getValue() {
            return this.name().toLowerCase();
        }

    }

    private interface StorageShadow {
        Collection<DynamoDbWriteRequest> flush();
    }

    private class SettingsShadow implements StorageShadow {

        private final DynamoDbRecord storageRecord;
        private String storageData;
        private String localData;
        private Settings localValue;

        public SettingsShadow(final DynamoDbRecord storageRecord) {
            this.storageRecord = storageRecord;
            this.storageData = new String(ZipUtils.decompress(storageRecord.data()));
            this.localData = this.storageData;
            this.localValue = jsonMapper.readValue(this.localData, Settings.class);
        }

        public SettingsShadow(final Settings localValue) {
            this.storageRecord = new DynamoDbRecord(
                    UUID.randomUUID().toString(),
                    RecordType.SETTINGS.getValue(),
                    null,
                    System.currentTimeMillis(),
                    null
            );
            this.storageData = null;
            this.localData = serialize(localValue, jsonMapper);
            this.localValue = localValue;
        }

        public void setValue(final Settings value) {
            this.localData = serialize(value, jsonMapper);
            this.localValue = value;
        }

        private static String serialize(
                final Settings value,
                final JsonMapper jsonMapper
        ) {
            return value == null
                    ? null
                    : jsonMapper.writeValueAsString(value);
        }

        public Settings getValue() {
            return localValue;
        }

        @Override
        public Collection<DynamoDbWriteRequest> flush() {
            if (Objects.equals(storageData, localData)) {
                return List.of();
            } else if (localData == null) {
                this.storageData = null;
                return List.of(DynamoDbWriteRequest.delete(storageRecord.id()));
            } else {
                this.storageData = localData;
                return List.of(DynamoDbWriteRequest.put(new DynamoDbRecord(
                        storageRecord.id(),
                        storageRecord.type(),
                        storageRecord.subject(),
                        System.currentTimeMillis(),
                        ZipUtils.compress(storageData.getBytes())
                )));
            }
        }

    }

    private class CommandsShadow implements StorageShadow {

        private final Map<String, Command> storageValue;
        private final Map<String, Command> localValue;

        public CommandsShadow(final Collection<DynamoDbRecord> records) {
            this.storageValue = records
                    .stream()
                    .map(record -> {
                        final CommandProperties properties = jsonMapper.readValue(
                                ZipUtils.decompress(record.data()),
                                CommandProperties.class
                        );
                        return new Command(
                                record.id(),
                                Long.valueOf(record.subject()),
                                record.time(),
                                properties.command(),
                                properties.parameters()
                        );
                    })
                    .collect(Collectors.toMap(
                            Command::id,
                            Function.identity()
                    ));
            this.localValue = new HashMap<>(storageValue);
        }

        public Collection<Command> getValue() {
            return localValue.values();
        }

        public void addCommand(
                final Long chatId,
                final Long time,
                final String command,
                final List<String> parameters
        ) {
            final String id = UUID.randomUUID().toString();
            localValue.put(
                    id,
                    new Command(
                            id,
                            chatId,
                            time,
                            command,
                            parameters
                    )
            );
        }

        public void removeCommand(final String id) {
            localValue.remove(id);
        }

        @Override
        public Collection<DynamoDbWriteRequest> flush() {
            final Collection<DynamoDbWriteRequest> writeRequests = Stream.of(storageValue.keySet(), localValue.keySet())
                    .flatMap(Collection::stream)
                    .distinct()
                    .map(id -> {
                        final Command storage = storageValue.get(id);
                        final Command local = localValue.get(id);
                        if (Objects.equals(storage, local)) {
                            return null;
                        } else if (local == null) {
                            return DynamoDbWriteRequest.delete(storage.id());
                        } else {
                            final CommandProperties properties = new CommandProperties(
                                    local.command(),
                                    local.parameters()
                            );
                            return DynamoDbWriteRequest.put(new DynamoDbRecord(
                                    id,
                                    RecordType.COMMAND.getValue(),
                                    String.valueOf(local.chatId()),
                                    local.time(),
                                    ZipUtils.compress(jsonMapper.writeValueAsBytes(properties))
                            ));
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            storageValue.clear();
            storageValue.putAll(localValue);
            return writeRequests;
        }

    }

}
