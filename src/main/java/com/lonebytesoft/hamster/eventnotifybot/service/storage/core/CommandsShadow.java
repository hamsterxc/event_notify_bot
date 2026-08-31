package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.CommandProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.CommandRecord;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.function.Function;

class CommandsShadow extends StorageShadow<CommandRecord> {

    public CommandsShadow(
            final Collection<DynamoDbRecord> records,
            final JsonMapper jsonMapper
    ) {
        super(records, entityBuilder(jsonMapper), recordBuilder(jsonMapper));
    }

    private static Function<DynamoDbRecord, CommandRecord> entityBuilder(final JsonMapper jsonMapper) {
        return record -> new CommandRecord(
                record.id(),
                Long.valueOf(record.subject()),
                record.time(),
                jsonMapper.readValue(ZipUtils.decompress(record.data()), CommandProperties.class)
        );
    }

    private static Function<CommandRecord, DynamoDbRecord> recordBuilder(final JsonMapper jsonMapper) {
        return entity -> new DynamoDbRecord(
                entity.id(),
                RecordType.COMMAND.getValue(),
                String.valueOf(entity.chatId()),
                entity.time(),
                ZipUtils.compress(jsonMapper.writeValueAsBytes(entity.properties()))
        );
    }

}
