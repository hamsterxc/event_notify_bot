package com.lonebytesoft.hamster.eventnotifybot.test.mock;

import com.lonebytesoft.hamster.eventnotifybot.handler.ApplicationContext;
import com.lonebytesoft.hamster.eventnotifybot.service.core.CommandParsingService;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

import java.time.InstantSource;
import java.util.Collection;

public class ApplicationContextMock extends ApplicationContext {

    private final StorageService storageService;
    private final TelegramService telegramService;
    private final CommandParsingService commandParsingService;
    private final InstantSource instantSource;
    private final Collection<Provider> providers;
    private final int writeCostLimit;

    public ApplicationContextMock(
            final StorageService storageService,
            final TelegramService telegramService,
            final CommandParsingService commandParsingService,
            final InstantSource instantSource,
            final Collection<Provider> providers,
            final int writeCostLimit
    ) {
        this.storageService = storageService;
        this.telegramService = telegramService;
        this.commandParsingService = commandParsingService;
        this.instantSource = instantSource;
        this.providers = providers;
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
    public InstantSource getInstantSource() {
        return instantSource;
    }

    @Override
    public Collection<Provider> getProviders() {
        return providers;
    }

    @Override
    public int getWriteCostLimit() {
        return writeCostLimit;
    }

}
