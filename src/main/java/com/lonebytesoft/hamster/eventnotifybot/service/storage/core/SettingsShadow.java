package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SettingsProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

class SettingsShadow extends StorageShadow<SettingsShadow.SettingsRecord> {

    private static final Logger log = LoggerFactory.getLogger(SettingsShadow.class);

    public SettingsShadow(
            final Collection<DynamoDbRecord> records,
            final JsonMapper jsonMapper
    ) {
        super(records, entityBuilder(jsonMapper), recordBuilder(jsonMapper));

        final Collection<SettingsRecord> settings = getAll();
        if (settings.size() > 1) {
            log.warn("Multiple settings records fetched, using the latest");
        }
    }

    private static Function<DynamoDbRecord, SettingsRecord> entityBuilder(final JsonMapper jsonMapper) {
        return record -> {
            final SettingsProperties properties = jsonMapper.readValue(
                    ZipUtils.decompress(record.data()),
                    SettingsProperties.class
            );
            final Settings settings = new Settings(properties.telegramUpdatesOffset());
            return new SettingsRecord(
                    record.id(),
                    record.time(),
                    settings
            );
        };
    }

    private static Function<SettingsRecord, DynamoDbRecord> recordBuilder(final JsonMapper jsonMapper) {
        return entity -> {
            final SettingsProperties properties = new SettingsProperties(
                    Optional.ofNullable(entity.settings())
                            .map(Settings::telegramUpdatesOffset)
                            .orElse(null)
            );
            return new DynamoDbRecord(
                    entity.id(),
                    RecordType.SETTINGS.getValue(),
                    null,
                    entity.time(),
                    ZipUtils.compress(jsonMapper.writeValueAsBytes(properties))
            );
        };
    }

    public Settings get() {
        return getAllSettings()
                .findFirst()
                .map(SettingsRecord::settings)
                .orElseGet(() -> new Settings(null));
    }

    public void set(final Settings settings) {
        final String id = getAllSettings()
                .findFirst()
                .map(SettingsRecord::id)
                .orElseGet(() -> UUID.randomUUID().toString());
        // if the new settings are the same as what is already in storage, prevent writing altogether
        final SettingsRecord newSettings = Optional.ofNullable(getFromStorage(id))
                .filter(storage -> Objects.equals(storage.settings(), settings))
                .orElseGet(() -> new SettingsRecord(
                        id,
                        System.currentTimeMillis(),
                        settings
                ));
        put(id, newSettings);
    }

    public void cleanup() {
        final Collection<String> cleanupIds = getAllSettings()
                .skip(1)
                .map(SettingsRecord::id)
                .toList();
        if (!cleanupIds.isEmpty()) {
            log.info("Cleaning up {} oldest settings records, leaving only the latest", cleanupIds.size());
            cleanupIds.forEach(this::remove);
        }
    }

    private Stream<SettingsRecord> getAllSettings() {
        return getAll()
                .stream()
                .sorted(Comparator.comparing(SettingsRecord::time).reversed()
                        .thenComparing(SettingsRecord::id));
    }

    @Override
    public Collection<DynamoDbWriteRequest> flush() {
        final Collection<DynamoDbWriteRequest> writeRequests = super.flush();
        writeRequests.forEach(writeRequest ->
                log.debug("Updating settings: {}", writeRequest));
        return writeRequests;
    }

    public record SettingsRecord(
            String id,
            Long time,
            Settings settings
    ) {
    }

}
