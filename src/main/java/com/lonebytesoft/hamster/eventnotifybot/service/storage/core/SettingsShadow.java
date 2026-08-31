package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SettingsProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.SettingsRecord;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.function.Function;

class SettingsShadow extends StorageShadow<SettingsRecord> {

    public SettingsShadow(
            final Collection<DynamoDbRecord> records,
            final JsonMapper jsonMapper
    ) {
        super(records, entityBuilder(jsonMapper), recordBuilder(jsonMapper));
    }

    private static Function<DynamoDbRecord, SettingsRecord> entityBuilder(final JsonMapper jsonMapper) {
        return record -> new SettingsRecord(
                record.id(),
                record.time(),
                jsonMapper.readValue(ZipUtils.decompress(record.data()), SettingsProperties.class)
        );
    }

    private static Function<SettingsRecord, DynamoDbRecord> recordBuilder(final JsonMapper jsonMapper) {
        return entity -> new DynamoDbRecord(
                entity.id(),
                RecordType.SETTINGS.getValue(),
                null,
                entity.time(),
                ZipUtils.compress(jsonMapper.writeValueAsBytes(entity.properties()))
        );
    }

}
