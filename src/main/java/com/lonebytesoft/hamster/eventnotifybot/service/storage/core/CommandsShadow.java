package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.CommandProperties;
import com.lonebytesoft.hamster.eventnotifybot.service.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.function.Function;

class CommandsShadow extends StorageShadow<Command> {

    private static final Logger log = LoggerFactory.getLogger(CommandsShadow.class);

    public CommandsShadow(
            final Collection<DynamoDbRecord> records,
            final JsonMapper jsonMapper
    ) {
        super(records, entityBuilder(jsonMapper), recordBuilder(jsonMapper));
    }

    private static Function<DynamoDbRecord, Command> entityBuilder(final JsonMapper jsonMapper) {
        return record -> {
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
        };
    }

    private static Function<Command, DynamoDbRecord> recordBuilder(final JsonMapper jsonMapper) {
        return entity -> {
            final CommandProperties properties = new CommandProperties(
                    entity.command(),
                    entity.parameters()
            );
            return new DynamoDbRecord(
                    entity.id(),
                    RecordType.COMMAND.getValue(),
                    String.valueOf(entity.chatId()),
                    entity.time(),
                    ZipUtils.compress(jsonMapper.writeValueAsBytes(properties))
            );
        };
    }

    public Collection<Command> getAll() {
        return super.getLocal();
    }

    public void add(final Command command) {
        super.put(command.id(), command);
    }

    public boolean remove(final String id) {
        return super.remove(id);
    }

    @Override
    public Collection<DynamoDbWriteRequest> flush() {
        final Collection<DynamoDbWriteRequest> writeRequests = super.flush();
        writeRequests.forEach(writeRequest ->
                log.debug("Updating command: {}", writeRequest));
        return writeRequests;
    }

}
