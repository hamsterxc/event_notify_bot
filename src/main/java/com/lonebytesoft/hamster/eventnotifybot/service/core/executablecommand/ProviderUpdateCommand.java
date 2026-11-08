package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

public record ProviderUpdateCommand(
        Provider provider
) implements ExecutableCommand {

    private static final Logger log = LoggerFactory.getLogger(ProviderUpdateCommand.class);

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        final String providerName = provider.name();
        provider.getData().ifPresentOrElse(
                data -> {
                    final Optional<Instant> nextUpdateTime = Optional.ofNullable(provider.getNextUpdateTime());
                    storageService.setProviderState(new ProviderState(
                            providerName,
                            nextUpdateTime
                                    .map(Instant::getEpochSecond)
                                    .orElse(null),
                            data
                    ));

                    final String nextUpdate = nextUpdateTime
                            .map(Instant::toString)
                            .orElse("(never)");
                    if (log.isTraceEnabled()) {
                        log.trace("Updated '{}' provider state, next update at {}, data: {}", providerName, nextUpdate, data);
                    } else {
                        log.info("Updated '{}' provider state, next update at {}", providerName, nextUpdate);
                    }
                },
                () -> log.warn("No data fetched for provider '{}', not updating provider state", providerName)
        );
        return true;
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return null;
    }

}
