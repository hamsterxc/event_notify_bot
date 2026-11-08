package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Subscription;
import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;

public final class SubscribeCommand extends AbstractProviderStateObserverCommand {

    private static final Logger log = LoggerFactory.getLogger(SubscribeCommand.class);

    private static final String MESSAGE_TEMPLATE = ResourceUtils.read("message/command/subscribe.html").trim();
    private static final String DUPLICATE_SUBSCRIPTION_TEMPLATE = ResourceUtils.read("message/command/subscribe_duplicate_subscription.html").trim();
    private static final String PROVIDER_PLACEHOLDER = "%PROVIDER%";

    private final long chatId;
    private final Provider provider;

    public SubscribeCommand(
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
                .map(providerData -> {
                    final Subscription subscription = getSubscription(providerData);
                    if (storageService.addSubscription(subscription)) {
                        final String message = MESSAGE_TEMPLATE
                                .replace(PROVIDER_PLACEHOLDER, provider.name());
                        telegramService.sendMessage(
                                chatId,
                                null,
                                message,
                                false
                        );
                        if (log.isTraceEnabled()) {
                            log.trace("Sent subscription success message to chat {}, subscription: {}", chatId, subscription);
                        } else {
                            log.debug("Sent subscription success message to chat {}", chatId);
                        }
                    } else {
                        getDuplicateSubscriptionCommand().execute(storageService, telegramService);
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
     * Calculate the total write cost of execution: creating a subscription and potentially updating the provider state.
     * Unlike {@link #execute}, considers provider state stale some time earlier than the scheduled update time
     * and cautiously counts in the cost of updating provider state to accommodate for any delays between these calls.
     * Possible duplicate subscriptions are not taken into consideration.
     */
    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        final Optional<String> providerData = getProviderDataWithBuffer(storageService, provider.name());
        return add(
                providerData.isPresent()
                        ? Integer.valueOf(0)
                        : getProviderUpdateCommand().estimateWriteCost(storageService),
                providerData
                        .map(state -> storageService.estimateWriteCost(getSubscription(state)))
                        .orElse(null)
        );
    }

    private static Integer add(final Integer first, final Integer second) {
        if ((first == null) || (second == null)) {
            return null;
        } else {
            return first + second;
        }
    }

    private Subscription getSubscription(final String data) {
        return new Subscription(
                chatId,
                provider.name(),
                data
        );
    }

    private ExecutableCommand getDuplicateSubscriptionCommand() {
        final String error = DUPLICATE_SUBSCRIPTION_TEMPLATE
                .replace(PROVIDER_PLACEHOLDER, provider.name());
        return new InvalidCommand(chatId, error);
    }

    private ExecutableCommand getProviderUpdateCommand() {
        return new ProviderUpdateCommand(provider);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SubscribeCommand that)) {
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
