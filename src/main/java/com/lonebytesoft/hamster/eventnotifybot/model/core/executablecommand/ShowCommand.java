package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public record ShowCommand(
        long chatId,
        Provider provider,
        InstantSource instantSource
) implements ExecutableCommand {

    private static final Logger log = LoggerFactory.getLogger(ShowCommand.class);

    private static final long PROVIDER_STATE_UPDATE_BUFFER_SECONDS = TimeUnit.MINUTES.toSeconds(1);

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        return getProviderState(storageService)
                .filter(providerState -> isActualState(providerState, 0))
                .map(ProviderState::data)
                .flatMap(data -> provider.getStateView(
                        data,
                        TelegramService.MAX_MESSAGE_TEXT_LENGTH,
                        TelegramService.MAX_MESSAGE_TEXT_WITH_IMAGE_LENGTH
                ))
                .map(providerView -> {
                    telegramService.sendMessage(
                            chatId,
                            providerView.imageUrl(),
                            providerView.textHtml(),
                            false
                    );
                    return true;
                })
                .orElseGet(() -> {
                    provider.getData()
                            .ifPresent(data -> {
                                final String providerName = provider.name();
                                final Instant nextUpdateTime = provider.getNextUpdateTime();
                                storageService.setProviderState(new ProviderState(
                                        providerName,
                                        Optional.ofNullable(nextUpdateTime).map(Instant::getEpochSecond).orElse(null),
                                        data
                                ));
                                log.info("Updated '{}' provider state, next update at {}", providerName, nextUpdateTime);
                            });
                    return false;
                });
    }

    /**
     * Return {@code null} if there is no known actual provider state (which will be written during {@link #execute}), {@code 0} otherwise.
     * Unlike {@link #execute}, considers provider state stale {@link #PROVIDER_STATE_UPDATE_BUFFER_SECONDS} seconds
     * earlier than the scheduled update time and cautiously reports cost as {@code null} (i.e., unknown) to accommodate for any delays between these calls.
     */
    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return getProviderState(storageService)
                .filter(providerState -> isActualState(providerState, PROVIDER_STATE_UPDATE_BUFFER_SECONDS))
                .isPresent() ? 0 : null;
    }

    private Optional<ProviderState> getProviderState(final StorageService storageService) {
        return storageService.getProviderStates()
                .stream()
                .filter(providerState -> Objects.equals(provider.name(), providerState.provider()))
                .min(Comparator.comparing(ProviderState::time, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private boolean isActualState(
            final ProviderState providerState,
            final long bufferSeconds
    ) {
        final Long time = providerState.time();
        return (time == null)
                || (instantSource.instant().getEpochSecond() + bufferSeconds < time);
    }

}
