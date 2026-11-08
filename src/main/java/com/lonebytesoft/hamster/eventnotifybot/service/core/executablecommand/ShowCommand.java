package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.InstantSource;
import java.util.Objects;

public class ShowCommand extends AbstractProviderStateObserverCommand {

    private static final Logger log = LoggerFactory.getLogger(ShowCommand.class);

    private final long chatId;
    private final Provider provider;

    public ShowCommand(
            final long chatId,
            final Provider provider,
            final InstantSource instantSource
    ) {
        super(instantSource);
        this.chatId = chatId;
        this.provider = provider;
    }

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        return getProviderData(storageService, provider.name())
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
                    if (log.isTraceEnabled()) {
                        log.trace("Sent provider '{}' state to chat {}: {}", provider.name(), chatId, providerView);
                    } else {
                        log.debug("Sent provider '{}' state to chat {}", provider.name(), chatId);
                    }
                    return true;
                })
                .orElseGet(() -> {
                    log.debug("No actual '{}' provider data, updating the state first", provider.name());
                    getProviderUpdateCommand().execute(storageService, telegramService);
                    return false;
                });
    }

    /**
     * Return {@code null} if there is no known actual provider state (which will be written during {@link #execute}), {@code 0} otherwise.
     * Unlike {@link #execute}, considers provider state stale some time earlier than the scheduled update time
     * and cautiously reports the cost of updating provider state to accommodate for any delays between these calls.
     */
    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return getProviderDataWithBuffer(storageService, provider.name()).isPresent()
                ? Integer.valueOf(0)
                : getProviderUpdateCommand().estimateWriteCost(storageService);
    }

    private ExecutableCommand getProviderUpdateCommand() {
        return new ProviderUpdateCommand(provider);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ShowCommand that)) {
            return false;
        }
        return (chatId == that.chatId)
                && Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                chatId,
                provider
        );
    }

}
