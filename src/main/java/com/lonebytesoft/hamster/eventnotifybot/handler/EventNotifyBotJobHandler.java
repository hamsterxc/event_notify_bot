package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import com.lonebytesoft.hamster.eventnotifybot.service.core.CommandParsingService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class EventNotifyBotJobHandler implements JobHandler {

    private final StorageService storageService;
    private final TelegramService telegramService;
    private final CommandParsingService commandParsingService;

    public EventNotifyBotJobHandler(
            final ApplicationContext applicationContext
    ) {
        this.storageService = applicationContext.getStorageService();
        this.telegramService = applicationContext.getTelegramService();
        this.commandParsingService = applicationContext.getCommandParsingService();
    }

    @Override
    public void run(Duration extraTimeout) throws Exception {
        final Settings settings = storageService.getSettings();

        final Long telegramUpdatesOffset = Optional.ofNullable(settings.telegramUpdatesOffset())
                .map(offset -> offset + 1)
                .orElse(null);
        final List<Update> updates = telegramService.getUpdates(telegramUpdatesOffset);
        updates
                .stream()
                .map(Update::message)
                .map(commandParsingService::parseMessage)
                .flatMap(Optional::stream)
                .map(commandParsingService::parseCommand)
                .flatMap(Optional::stream)
                .forEach(command -> command.execute(storageService, telegramService));
        final Long nextTelegramUpdatesOffset = updates
                .stream()
                .map(Update::id)
                .max(Long::compareTo)
                .orElseGet(settings::telegramUpdatesOffset);

        storageService.setSettings(new Settings(nextTelegramUpdatesOffset));
        storageService.flush();
    }

}
