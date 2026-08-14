package com.lonebytesoft.hamster.eventnotifybot.service.storage;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final int WCU_LIMIT = 25;
    private static final int WCU_WARN_LIMIT_PERCENTAGE = 80;
    private static final int WCU_WARN_LIMIT = (int) Math.ceil((double) WCU_LIMIT * WCU_WARN_LIMIT_PERCENTAGE / 100);

    private final DynamoDbService dynamoDbService;
    private final JsonMapper jsonMapper;

    private SettingsShadow settings = new SettingsShadow((Settings) null);
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

        return dynamoDbReadResponse.consumedCapacity();
    }

    public int flush() {
        Optional.ofNullable(settings.flush())
                .filter(writeRequests -> !writeRequests.isEmpty())
                .ifPresent(writeRequests -> {
                    log.debug("Updating settings to {}", settings.getValue());
                    this.writeRequests.addAll(writeRequests);
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

    private enum RecordType {

        SETTINGS,
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

}
