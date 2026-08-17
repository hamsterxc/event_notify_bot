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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

class SettingsShadow extends StorageShadow<SettingsShadow.SettingsRecord> {

    private static final Logger log = LoggerFactory.getLogger(SettingsShadow.class);

    public SettingsShadow(
            final Collection<DynamoDbRecord> records,
            final JsonMapper jsonMapper
    ) {
        super(records, entityBuilder(jsonMapper), recordBuilder(jsonMapper));

        final Collection<SettingsRecord> settings = getAll();
        switch (settings.size()) {
            case 0:
                final Settings defaultSettings = defaultSettings();
                log.info("No settings fetched, falling back to default: {}", settings);
                set(defaultSettings);
                break;

            case 1:
                log.debug("Fetched settings: {}", settings.iterator().next().settings());
                break;

            default:
                log.warn("Multiple settings records fetched, leaving the latest and removing all others: {}", settings);
                final List<SettingsRecord> sortedSettings = settings
                        .stream()
                        .sorted(Comparator.comparing(SettingsRecord::time).reversed()
                                .thenComparing(SettingsRecord::id))
                        .toList();
                sortedSettings
                        .stream()
                        .skip(1)
                        .map(SettingsRecord::id)
                        .forEach(this::remove);
                break;
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
        return getAll()
                .stream()
                .findFirst()
                .map(SettingsRecord::settings)
                .orElseGet(SettingsShadow::defaultSettings);
    }

    public void set(final Settings settings) {
        final String id = getAll()
                .stream()
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

    private static Settings defaultSettings() {
        return new Settings(null);
    }

    @Override
    public Collection<DynamoDbWriteRequest> flush() {
        final Collection<DynamoDbWriteRequest> writeRequests = super.flush();
        if (!writeRequests.isEmpty()) {
            log.debug("Updating settings: {}", get());
        }
        return writeRequests;
    }

    public record SettingsRecord(
            String id,
            Long time,
            Settings settings
    ) {
    }

}
