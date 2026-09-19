package com.lonebytesoft.hamster.eventnotifybot.test;

import com.lonebytesoft.hamster.eventnotifybot.handler.ApplicationContext;
import com.lonebytesoft.hamster.eventnotifybot.service.core.CommandParsingService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

public class ApplicationContextMock extends ApplicationContext {

    private final StorageService storageService;
    private final TelegramService telegramService;
    private final CommandParsingService commandParsingService;
    private final int writeCostLimit;

    public ApplicationContextMock(
            final StorageService storageService,
            final TelegramService telegramService,
            final CommandParsingService commandParsingService,
            final int writeCostLimit
    ) {
        this.storageService = storageService;
        this.telegramService = telegramService;
        this.commandParsingService = commandParsingService;
        this.writeCostLimit = writeCostLimit;
    }

    @Override
    public StorageService getStorageService() {
        return storageService;
    }

    @Override
    public TelegramService getTelegramService() {
        return telegramService;
    }

    @Override
    public CommandParsingService getCommandParsingService() {
        return commandParsingService;
    }

    @Override
    public int getWriteCostLimit() {
        return writeCostLimit;
    }

}
